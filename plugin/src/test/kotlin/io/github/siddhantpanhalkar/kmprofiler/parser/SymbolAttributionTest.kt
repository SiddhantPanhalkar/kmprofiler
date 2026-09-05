package io.github.siddhantpanhalkar.kmprofiler.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SymbolAttributionTest {

    private val parser = LinkMapParser()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `attributeSymbols maps symbols to object files`() {
        val linkMap = writeLinkMap(
            """
            # Object files:
            [  1] /Users/test/Shared.framework/Shared
            [  2] /Users/test/App.o
            [  3] /path/to/Ktor_client_coreHttpClient.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:com.example.Foo#bar(){}
            0x100004100	0x00000200	[  2]	_OBJC_CLASS_${'$'}_SharedUserRepo
            0x100004300	0x00000400	[  3]	_kfun:io.ktor.HttpClient#get(){}
            """.trimIndent()
        )

        val result = parser.parse(linkMap, "Shared", collectSymbols = true)
        val attributions = parser.attributeSymbols(result)

        assertThat(attributions).hasSize(3)

        val foo = attributions.first { it.symbolName.contains("Foo") }
        assertThat(foo.objectFileIndex).isEqualTo(1)
        assertThat(foo.objectFilePath).contains("Shared.framework/Shared")
        assertThat(foo.moduleName).isEqualTo("Shared")

        val objc = attributions.first { it.symbolName.contains("SharedUserRepo") }
        assertThat(objc.objectFileIndex).isEqualTo(2)
        assertThat(objc.moduleName).isEqualTo("App")

        val ktor = attributions.first { it.symbolName.contains("HttpClient") }
        assertThat(ktor.objectFileIndex).isEqualTo(3)
        assertThat(ktor.moduleName).isEqualTo("Ktor_client_coreHttpClient")
    }

    @Test
    fun `attributeSymbols sorts by size descending`() {
        val linkMap = writeLinkMap(
            """
            # Object files:
            [  1] /path/to/Foo.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:com.example#small(){}
            0x100004100	0x00000400	[  1]	_kfun:com.example#large(){}
            0x100004500	0x00000200	[  1]	_kfun:com.example#medium(){}
            """.trimIndent()
        )

        val result = parser.parse(linkMap, "Shared", collectSymbols = true)
        val attributions = parser.attributeSymbols(result)

        assertThat(attributions[0].sizeBytes).isGreaterThanOrEqualTo(attributions[1].sizeBytes)
        assertThat(attributions[1].sizeBytes).isGreaterThanOrEqualTo(attributions[2].sizeBytes)
    }

    @Test
    fun `attributeSymbols extracts module name from framework path`() {
        val linkMap = writeLinkMap(
            """
            # Object files:
            [  1] /path/to/Koin_coreModule.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[  1]	_kfun:koin.Module#inject(){}
            """.trimIndent()
        )

        val result = parser.parse(linkMap, "Shared", collectSymbols = true)
        val attributions = parser.attributeSymbols(result)

        assertThat(attributions[0].moduleName).isEqualTo("Koin_coreModule")
    }

    @Test
    fun `attributeSymbols handles unknown object file index gracefully`() {
        val linkMap = writeLinkMap(
            """
            # Object files:
            [  1] /path/to/Foo.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000100	[ 99]	_kfun:com.example#bar(){}
            """.trimIndent()
        )

        val result = parser.parse(linkMap, "Shared", collectSymbols = true)
        val attributions = parser.attributeSymbols(result)

        assertThat(attributions[0].objectFilePath).isEqualTo("unknown")
        assertThat(attributions[0].moduleName).isEqualTo("unknown")
    }

    private fun writeLinkMap(content: String): File {
        return tempDir.resolve("test-linkmap.txt").apply { writeText(content) }
    }
}
