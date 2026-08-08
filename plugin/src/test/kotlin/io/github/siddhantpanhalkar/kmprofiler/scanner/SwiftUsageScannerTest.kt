package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
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

        assertThat(result).isEmpty()
    }

    @Test
    fun `selector used as swift method call is not a candidate`() {
        writeSwift(tempDir, "Driver.swift", "driver.doWith(a: 1, b: 2)")

        val result = scanner.findUnreferenced(listOf(decl("Driver", "doWith:b:")), setOf(tempDir))

        assertThat(result).isEmpty()
    }

    @Test
    fun `declaration not mentioned is a candidate`() {
        writeSwift(tempDir, "ContentView.swift", "let engine = AuthEngine()")

        val result = scanner.findUnreferenced(listOf(decl("UnusedType")), setOf(tempDir))

        assertThat(result.map { it.name }).containsExactly("UnusedType")
    }

    @Test
    fun `empty swift dirs mark all declarations as candidates`() {
        val result = scanner.findUnreferenced(
            listOf(decl("A"), decl("B")),
            emptySet(),
        )

        assertThat(result.map { it.name }).containsExactly("A", "B")
    }

    @Test
    fun `swift file in nested subdirectory is found`() {
        writeSwift(
            tempDir,
            "Views/Detail/DetailView.swift",
            "class DetailView { let w = Widget() }"
        )

        val result = scanner.findUnreferenced(listOf(decl("Widget")), setOf(tempDir))

        assertThat(result).isEmpty()
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

        assertThat(result).isEmpty()
    }
}