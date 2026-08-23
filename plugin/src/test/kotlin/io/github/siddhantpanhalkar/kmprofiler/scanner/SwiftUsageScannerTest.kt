package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SwiftUsageScannerTest {

    @TempDir
    lateinit var tempDir: File

    private val scanner = SwiftUsageScanner()

    private fun declaration(name: String, vararg selectors: String) =
        ExportedDeclaration(name, DeclarationKind.CLASS, selectors.toList(), selectors.size)

    private fun writeSwift(relativePath: String, content: String): File {
        val file = tempDir.resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText(content.trimIndent())
        return file
    }

    @Test
    fun `records file and line evidence for a standalone type token`() {
        writeSwift(
            "ContentView.swift",
            """
            import Shared
            let engine: AuthEngine = AuthEngine()
            """,
        )

        val result =
            scanner.scan(listOf(declaration("AuthEngine")), setOf(tempDir)).results.single()

        assertThat(result.status).isEqualTo(MatchKind.TYPE_REFERENCE)
        assertThat(result.evidence).allSatisfy {
            assertThat(it.filePath).endsWith("ContentView.swift")
            assertThat(it.lineNumber).isEqualTo(2)
            assertThat(it.matchedText).isEqualTo("AuthEngine")
        }
    }

    @Test
    fun `does not match a declaration name embedded in another identifier`() {
        writeSwift("ContentView.swift", "let value = AuthEngineFactory()")

        val result =
            scanner.scan(listOf(declaration("AuthEngine")), setOf(tempDir)).results.single()

        assertThat(result.status).isEqualTo(MatchKind.NO_REFERENCE)
    }

    @Test
    fun `does not treat a globally matched member selector as a type reference`() {
        writeSwift("Driver.swift", "driver.start()")

        val result = scanner.scan(
            listOf(declaration("ZoomController", "start")),
            setOf(tempDir),
        ).results.single()

        assertThat(result.status).isEqualTo(MatchKind.NO_REFERENCE)
        assertThat(result.isReferenced).isFalse()
    }

    @Test
    fun `does not match comments or normal and multiline string literals`() {
        writeSwift(
            "ContentView.swift",
            "// AuthEngine()\n" +
                    "let message = \"AuthEngine\"\n" +
                    "let documentation = \"\"\"\n" +
                    "AuthEngine\n" +
                    "\"\"\"\n" +
                    "/* AuthEngine */",
        )

        val result =
            scanner.scan(listOf(declaration("AuthEngine")), setOf(tempDir)).results.single()

        assertThat(result.status).isEqualTo(MatchKind.NO_REFERENCE)
    }

    @Test
    fun `collects swift files from source files and directories deterministically`() {
        val directFile = writeSwift("Top.swift", "let a = TypeA()")
        writeSwift("Views/Detail.swift", "let b = TypeB()")

        val output = scanner.scan(
            listOf(declaration("TypeA"), declaration("TypeB")),
            linkedSetOf(tempDir.resolve("Views"), directFile),
        )

        assertThat(output.scannedFileCount).isEqualTo(2)
        assertThat(output.unreferenced).isEmpty()
    }
}
