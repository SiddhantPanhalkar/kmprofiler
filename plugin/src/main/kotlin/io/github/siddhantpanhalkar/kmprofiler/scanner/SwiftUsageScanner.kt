package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import io.github.siddhantpanhalkar.kmprofiler.model.ScanEvidence
import java.io.File

/**
 * Finds standalone exported declaration names in configured Swift source files.
 *
 * This is a source-text audit, not a reachability analysis. It intentionally does
 * not use member selector names as evidence: `start()` does not establish which
 * type owns that call. Comments and string literals are excluded from matching.
 */
class SwiftUsageScanner {

    data class ScanOutput(
        val results: List<DeclarationScanResult>,
        val scannedFileCount: Int,
        val scannedLineCount: Int,
        val sourceRoots: Set<String>,
    ) {
        val unreferenced: List<DeclarationScanResult>
            get() = results.filterNot { it.isReferenced }
    }

    fun scan(
        declarations: List<ExportedDeclaration>,
        swiftSourceDirs: Set<File>,
    ): ScanOutput {
        val files = collectSwiftFiles(swiftSourceDirs)
        val contents = files.map { file -> FileContent(file.absolutePath, file.readText()) }

        return ScanOutput(
            results = declarations.map { declaration -> analyzeDeclaration(declaration, contents) },
            scannedFileCount = files.size,
            scannedLineCount = contents.sumOf { it.text.lineSequence().count() },
            sourceRoots = swiftSourceDirs.map { it.absolutePath }.toSortedSet(),
        )
    }

    /** Kept for source compatibility with the v0.1 task implementation. */
    fun findUnreferenced(
        declarations: List<ExportedDeclaration>,
        swiftSourceDirs: Set<File>,
    ): ScanOutput = scan(declarations, swiftSourceDirs)

    private data class FileContent(val path: String, val text: String)

    private fun collectSwiftFiles(roots: Set<File>): List<File> = roots
        .flatMap { root ->
            when {
                root.isFile && root.extension.equals("swift", ignoreCase = true) -> listOf(root)
                root.isDirectory -> root.walkTopDown()
                    .filter { it.isFile && it.extension.equals("swift", ignoreCase = true) }
                    .toList()

                else -> emptyList()
            }
        }
        .distinctBy { it.absoluteFile.normalize().path }
        .sortedBy { it.absolutePath }

    private fun analyzeDeclaration(
        declaration: ExportedDeclaration,
        files: List<FileContent>,
    ): DeclarationScanResult {
        val matches = files.flatMap { file -> findTypeReferences(declaration.name, file) }
        return DeclarationScanResult(
            declaration = declaration,
            status = if (matches.isEmpty()) MatchKind.NO_REFERENCE else MatchKind.TYPE_REFERENCE,
            evidence = matches,
        )
    }

    private fun findTypeReferences(name: String, file: FileContent): List<ScanEvidence> {
        if (name.isBlank()) return emptyList()
        val tokenPattern = nameTokenPattern(name)
        return executableLines(file.text).mapIndexedNotNull { index, line ->
            if (!tokenPattern.containsMatchIn(line)) return@mapIndexedNotNull null
            ScanEvidence(
                filePath = file.path,
                lineNumber = index + 1,
                matchKind = MatchKind.TYPE_REFERENCE,
                matchedText = name,
            )
        }
    }

    /**
     * Replaces comments and string contents with spaces while preserving line numbers.
     * It covers line comments, block comments, normal strings, and multiline strings.
     */
    private fun executableLines(source: String): List<String> {
        val output = StringBuilder(source.length)
        var index = 0
        var blockComment = false
        var multilineString = false

        while (index < source.length) {
            if (blockComment) {
                if (source.startsWith("*/", index)) {
                    output.append("  ")
                    index += 2
                    blockComment = false
                } else {
                    output.append(if (source[index] == '\n') '\n' else ' ')
                    index++
                }
                continue
            }
            if (multilineString) {
                if (source.startsWith("\"\"\"", index)) {
                    output.append("   ")
                    index += 3
                    multilineString = false
                } else {
                    output.append(if (source[index] == '\n') '\n' else ' ')
                    index++
                }
                continue
            }
            when {
                source.startsWith("//", index) -> {
                    while (index < source.length && source[index] != '\n') {
                        output.append(' ')
                        index++
                    }
                }

                source.startsWith("/*", index) -> {
                    output.append("  ")
                    index += 2
                    blockComment = true
                }

                source.startsWith("\"\"\"", index) -> {
                    output.append("   ")
                    index += 3
                    multilineString = true
                }

                source[index] == '\"' -> {
                    output.append(' ')
                    index++
                    var escaped = false
                    while (index < source.length) {
                        val character = source[index]
                        output.append(if (character == '\n') '\n' else ' ')
                        index++
                        if (character == '\"' && !escaped) break
                        escaped = character == '\\' && !escaped
                        if (character != '\\') escaped = false
                    }
                }

                else -> {
                    output.append(source[index])
                    index++
                }
            }
        }
        return output.toString().lines()
    }

    companion object {
        fun nameTokenPattern(word: String): Regex =
            Regex("(?<![A-Za-z0-9_])\\Q$word\\E(?![A-Za-z0-9_])")
    }
}
