package io.github.siddhantpanhalkar.kmprofiler.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinkMapParserTest {

    private val parser = LinkMapParser()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `parses link map and correctly aggregates bytes for packages and iOS export surface`() {
        val linkMapContent = """
            # Path: /Users/test/Build/Products/Release-iphoneos/App.app/App
            # Arch: arm64
            # Object files:
            [  0] linker synthesized
            [  1] /Users/test/Shared.framework/Shared
            [  2] /Users/test/App.o

            # Sections:
            # Address	Size    	Segment	Section
            0x100004000	0x00001000	__TEXT	__text

            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000020	[  1]	_kfun:com.example.Foo#bar(){}
            0x100004020	0x00000030	[  1]	_kfun:com.example.Baz#qux(){}
            0x100004050	0x00000100	[  1]	_kclass:kotlinx.cinterop.CPointer
            0x100004150	0x00000080	[  2]	_OBJC_CLASS_${'$'}_SharedUserRepository
            0x1000041D0	0x00000040	[  2]	_OBJC_METACLASS_${'$'}_SharedUserRepository
            0x100004210	0x00000010	[  2]	_OBJC_IVAR_${'$'}_SharedUserRepository._nativePointer
            0x100004220	0x00000200	[  3]	_main
            # Dead Stripped Symbols:
            # Address	Size    	File  Name
            0x100004420	0x00000050	<<dead>>	_kfun:com.example#deadFunc
        """.trimIndent()

        val mapFile = tempDir.resolve("App-LinkMap-normal-arm64.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")

        assertThat(result.categories["kotlinx.cinterop"]).isEqualTo(256L)
        assertThat(result.categories["[iOS Export Surface]"]).isEqualTo(208L)
        assertThat(result.categories["com.example"]).isEqualTo(80L)
        assertThat(result.categories.containsKey("_main")).isFalse()

        // Verify sorted descending order
        assertThat(result.categories.keys).containsExactly(
            "kotlinx.cinterop",
            "[iOS Export Surface]",
            "com.example",
        )

        // Verify coverage stats
        assertThat(result.symbolCount).isEqualTo(7)
        assertThat(result.classifiedSymbolCount).isEqualTo(6)
        assertThat(result.totalMappedBytes).isEqualTo(1056L)
        assertThat(result.classifiedBytes).isEqualTo(544L)
        assertThat(result.unclassifiedBytes).isEqualTo(512L)
    }

    @Test
    fun `parses space separated link map entries`() {
        val linkMapContent = """
            # Symbols:
            # Address Size File Name
            0x100004000 0x00000040 [ 1] _kfun:com.example.Service#run
            0x100004040 0x00000020 [ 1] _OBJC_CLASS_${'$'}_SharedService
        """.trimIndent()

        val mapFile = tempDir.resolve("space-separated.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")

        assertThat(result.categories["com.example"]).isEqualTo(64L)
        assertThat(result.categories["[iOS Export Surface]"]).isEqualTo(32L)
        assertThat(result.symbolCount).isEqualTo(2)
        assertThat(result.classifiedSymbolCount).isEqualTo(2)
    }

    @Test
    fun `parses all kotlin symbol prefixes`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000010	[  1]	_kfun:io.ktor.client.HttpClient#get
            0x100004010	0x00000020	[  1]	_kclass:io.ktor.client.HttpClient
            0x100004030	0x00000030	[  1]	_ktype:io.ktor.client.engine.Engine
            0x100004060	0x00000040	[  1]	_kvar:io.ktor.client.defaultClient
            0x1000040A0	0x00000050	[  1]	_kext:io.ktor.client.call
        """.trimIndent()

        val mapFile = tempDir.resolve("kotlin-prefixes.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")

        assertThat(result.categories["io.ktor"]).isEqualTo(240L)
        assertThat(result.symbolCount).isEqualTo(5)
        assertThat(result.classifiedSymbolCount).isEqualTo(5)
    }

    @Test
    fun `ignores objc symbols when framework prefix does not match`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000050	[  1]	_OBJC_CLASS_${'$'}_OtherFrameworkClass
        """.trimIndent()

        val mapFile = tempDir.resolve("other-framework.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")

        assertThat(result.categories).isEmpty()
        assertThat(result.symbolCount).isEqualTo(1)
        assertThat(result.classifiedSymbolCount).isEqualTo(0)
    }

    @Test
    fun `returns empty result for non-existent file`() {
        val nonExistent = tempDir.resolve("missing.txt")
        val result = parser.parse(nonExistent, "Shared")
        assertThat(result.categories).isEmpty()
        assertThat(result.symbolCount).isEqualTo(0)
    }

    @Test
    fun `returns empty result when no symbols section is present`() {
        val linkMapContent = """
            # Path: /Users/test/App.app/App
            # Arch: arm64
            # Sections:
            0x100004000	0x00001000	__TEXT	__text
        """.trimIndent()

        val mapFile = tempDir.resolve("no-symbols.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, "Shared")
        assertThat(result.categories).isEmpty()
    }

    @Test
    fun `handles single segment package names`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000010	[  1]	_kfun:rootFunction#()
        """.trimIndent()

        val mapFile = tempDir.resolve("root-package.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, "Shared")
        assertThat(result.categories["rootFunction"]).isEqualTo(16L)
    }

    @Test
    fun `reports coverage percentage correctly`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:com.example.Foo#bar(){}
            0x100004100	0x00000080	[  1]	_OBJC_CLASS_${'$'}_SharedService
            0x100004180	0x00000020	[  2]	_something_unclassified
        """.trimIndent()

        val mapFile = tempDir.resolve("coverage-test.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")

        // 256 + 128 + 32 = 416 total, 256 + 128 = 384 classified
        assertThat(result.totalMappedBytes).isEqualTo(416L)
        assertThat(result.classifiedBytes).isEqualTo(384L)
        assertThat(result.unclassifiedBytes).isEqualTo(32L)
        assertThat(result.coveragePercentage).isCloseTo(
            92.3,
            org.assertj.core.data.Offset.offset(0.1)
        )
    }

    @Test
    fun `returns zero coverage for empty file`() {
        val mapFile = tempDir.resolve("empty.txt")
        mapFile.writeText("")

        val result = parser.parse(mapFile, "Shared")
        assertThat(result.coveragePercentage).isEqualTo(0.0)
        assertThat(result.totalMappedBytes).isEqualTo(0L)
    }

    @Test
    fun `handles truncated link map gracefully`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:com.example.Foo#bar(){}
            # abruptly ends here with no closing section
        """.trimIndent()

        val mapFile = tempDir.resolve("truncated.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")
        assertThat(result.categories["com.example"]).isEqualTo(256L)
        assertThat(result.symbolCount).isEqualTo(1)
    }

    @Test
    fun `handles hex size variants without 0x prefix`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	00000100	[  1]	_kfun:com.example.Foo#bar(){}
        """.trimIndent()

        val mapFile = tempDir.resolve("hex-no-prefix.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")
        assertThat(result.categories["com.example"]).isEqualTo(256L)
    }

    @Test
    fun `validates file looks like link map before full parse`() {
        val notALinkMap = tempDir.resolve("not-a-linkmap.txt")
        notALinkMap.writeText("This is just a regular text file.\nNo link map content here.")

        val result = parser.parse(notALinkMap, "Shared")
        assertThat(result.categories).isEmpty()
        assertThat(result.isValid).isFalse()
        assertThat(result.symbolCount).isEqualTo(0L)
    }

    @Test
    fun `collectSymbols = false does not populate symbols list`() {
        val linkMapContent = """
            # Path: /App
            # Arch: arm64
            # Object files:
            [  1] /Shared.framework/Shared
            # Symbols:
            # Address Size File Name
            0x100004000 0x00000020 [  1] _kfun:Foo
        """.trimIndent()
        val mapFile = tempDir.resolve("test-no-collect.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared", collectSymbols = false)
        assertThat(result.symbols).isEmpty()
        assertThat(result.symbolCount).isEqualTo(1L)
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `invalid file without Symbols header has isValid = false and fails when compare called`() {
        val mapFile = tempDir.resolve("invalid.txt")
        mapFile.writeText("# Path: /App")

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")
        assertThat(result.isValid).isFalse()

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            parser.compare(mapFile, mapFile, "Shared")
        }
    }

    @Test
    fun `dead stripped symbols are ignored`() {
        val linkMapContent = """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:com.example.Foo#live(){}
            # Dead Stripped Symbols:
            # Address	Size    	File  Name
            <<dead>> 	0x00000200	[  1]	_kfun:com.example.Foo#dead(){}
        """.trimIndent()

        val mapFile = tempDir.resolve("dead-stripped.txt")
        mapFile.writeText(linkMapContent)

        val result = parser.parse(mapFile, frameworkPrefix = "Shared")
        assertThat(result.categories["com.example"]).isEqualTo(256L)
        assertThat(result.symbolCount).isEqualTo(1L)
    }
}
