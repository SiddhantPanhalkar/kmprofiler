package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SwiftUsageScannerTest {

    private val scanner = SwiftUsageScanner()

    private fun decl(name: String, vararg selectors: String) =
        ExportedDeclaration(name, DeclarationKind.CLASS, selectors.toList(), selectors.size)

    @TempDir
    lateinit var tempDir: File

    private fun writeSwift(dir: File, relativePath: String, content: String): File {
        val file = dir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `class name appearing in swift file is not a candidate`() {
        writeSwift(tempDir, "ContentView.swift", "let engine = AuthEngine()")

        val result = scanner.findUnreferenced(listOf(decl("AuthEngine")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
        assertThat(result.referenced).hasSize(1)
        assertThat(result.referenced[0].status).isEqualTo(MatchKind.TYPE_REFERENCE)
        assertThat(result.referenced[0].confidence.name).isEqualTo("HIGH")
    }

    @Test
    fun `selector used as swift method call is not a candidate`() {
        writeSwift(tempDir, "Driver.swift", "driver.doWith(a: 1, b: 2)")

        val result = scanner.findUnreferenced(listOf(decl("Driver", "doWith:b:")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
        assertThat(result.referenced[0].status).isEqualTo(MatchKind.MEMBER_REFERENCE)
    }

    @Test
    fun `declaration not mentioned is a candidate`() {
        writeSwift(tempDir, "ContentView.swift", "let engine = AuthEngine()")

        val result = scanner.findUnreferenced(listOf(decl("UnusedType")), setOf(tempDir))

        assertThat(result.unreferenced).hasSize(1)
        assertThat(result.unreferenced[0].name).isEqualTo("UnusedType")
        assertThat(result.unreferenced[0].status).isEqualTo(MatchKind.NO_REFERENCE)
    }

    @Test
    fun `empty swift dirs mark all declarations as candidates`() {
        val result = scanner.findUnreferenced(
            listOf(decl("A"), decl("B")),
            emptySet(),
        )

        assertThat(result.unreferenced.map { it.name }).containsExactly("A", "B")
        assertThat(result.scannedFileCount).isEqualTo(0)
    }

    @Test
    fun `swift file in nested subdirectory is found`() {
        writeSwift(
            tempDir,
            "Views/Detail/DetailView.swift",
            "class DetailView { let w = Widget() }"
        )

        val result = scanner.findUnreferenced(listOf(decl("Widget")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `multiple source dirs are searched as a union`() {
        val dirA = tempDir.resolve("a").apply { mkdirs() }
        val dirB = tempDir.resolve("b").apply { mkdirs() }
        writeSwift(dirA, "One.swift", "let x = TypeA()")
        writeSwift(dirB, "Two.swift", "let y = TypeB()")

        val result = scanner.findUnreferenced(
            listOf(decl("TypeA"), decl("TypeB")),
            setOf(dirA, dirB),
        )

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `scan output reports file and line counts`() {
        writeSwift(tempDir, "A.swift", "line1\nline2\nline3")
        writeSwift(tempDir, "B.swift", "line1\nline2")

        val result = scanner.findUnreferenced(listOf(decl("Unused")), setOf(tempDir))

        assertThat(result.scannedFileCount).isEqualTo(2)
        assertThat(result.scannedLineCount).isGreaterThanOrEqualTo(5)
        assertThat(result.sourceRoots).containsExactly(tempDir.absolutePath)
    }

    @Test
    fun `name in comment is stripped and not found`() {
        writeSwift(tempDir, "View.swift", "// This uses AuthEngine for auth")

        val result = scanner.findUnreferenced(listOf(decl("AuthEngine")), setOf(tempDir))

        // Comments are stripped before matching, so name is not found in executable code
        assertThat(result.unreferenced).hasSize(1)
        assertThat(result.unreferenced[0].status).isEqualTo(MatchKind.NO_REFERENCE)
    }

    @Test
    fun `name in string literal produces weak evidence only`() {
        writeSwift(tempDir, "View.swift", "let name = \"AuthEngine\"")

        val result = scanner.findUnreferenced(listOf(decl("AuthEngine")), setOf(tempDir))

        // String literals are stripped, so name is not found in executable code
        assertThat(result.unreferenced).hasSize(1)
        assertThat(result.unreferenced[0].status).isEqualTo(MatchKind.NO_REFERENCE)
    }

    @Test
    fun `partial name match is not a candidate`() {
        writeSwift(tempDir, "View.swift", "let fooBar = Foo()")

        val result = scanner.findUnreferenced(listOf(decl("Foo")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `name embedded in longer identifier is not found`() {
        writeSwift(tempDir, "View.swift", "let fooBarBaz = 1")

        val result = scanner.findUnreferenced(listOf(decl("fooBar")), setOf(tempDir))

        // Word-boundary matching: "fooBar" is not a standalone token in "fooBarBaz"
        assertThat(result.unreferenced).hasSize(1)
        assertThat(result.unreferenced[0].name).isEqualTo("fooBar")
    }

    @Test
    fun `nonexistent swift dir is handled gracefully`() {
        val nonExistent = tempDir.resolve("nonexistent")

        val result = scanner.findUnreferenced(listOf(decl("A")), setOf(nonExistent))

        assertThat(result.unreferenced.map { it.name }).containsExactly("A")
        assertThat(result.scannedFileCount).isEqualTo(0)
    }

    @Test
    fun `evidence records file path and line number`() {
        writeSwift(tempDir, "ContentView.swift", "let x = MyClass()\nlet y = Other()")

        val result = scanner.findUnreferenced(listOf(decl("MyClass")), setOf(tempDir))

        assertThat(result.referenced).hasSize(1)
        val evidence = result.referenced[0].evidence
        assertThat(evidence).hasSize(1)
        assertThat(evidence[0].filePath).endsWith("ContentView.swift")
        assertThat(evidence[0].lineNumber).isEqualTo(1)
        assertThat(evidence[0].matchedText).isEqualTo("MyClass")
    }

    @Test
    fun `multiple evidence entries for declaration found in multiple files`() {
        writeSwift(tempDir, "A.swift", "let x = SharedType()")
        writeSwift(tempDir, "B.swift", "let y = SharedType()")

        val result = scanner.findUnreferenced(listOf(decl("SharedType")), setOf(tempDir))

        assertThat(result.referenced).hasSize(1)
        assertThat(result.referenced[0].evidence).hasSize(2)
    }

    @Test
    fun `protocol conformance reference is detected`() {
        writeSwift(tempDir, "Delegates.swift", "class MyDelegate: SomeProtocol { }")

        val result = scanner.findUnreferenced(listOf(decl("SomeProtocol")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `extension reference is detected`() {
        writeSwift(tempDir, "Ext.swift", "extension MyClass { fun doStuff() {} }")

        val result = scanner.findUnreferenced(listOf(decl("MyClass")), setOf(tempDir))

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `overloaded selector matching`() {
        writeSwift(tempDir, "View.swift", "obj.process(input: data)")

        val result = scanner.findUnreferenced(
            listOf(decl("Worker", "process:")),
            setOf(tempDir),
        )

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `Swift renamed declaration matching`() {
        writeSwift(tempDir, "View.swift", "let x = swiftName()")

        val result = scanner.findUnreferenced(
            listOf(decl("swiftName")),
            setOf(tempDir),
        )

        assertThat(result.unreferenced).isEmpty()
    }

    @Test
    fun `Unicode identifier matching`() {
        writeSwift(tempDir, "View.swift", "let x = café()")

        val result = scanner.findUnreferenced(
            listOf(decl("café")),
            setOf(tempDir),
        )

        assertThat(result.unreferenced).isEmpty()
    }
}
