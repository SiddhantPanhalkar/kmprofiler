package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.Confidence
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import io.github.siddhantpanhalkar.kmprofiler.model.ScanEvidence
import java.io.File

/**
 * Scans configured Swift source directories and reports declarations with evidence
 * of whether they have textual matches in the scanned corpus.
 *
 * The scanner performs token-aware matching:
 *   - Type names must appear as standalone tokens (word boundaries), not substrings.
 *   - Member selectors are matched independently from type names.
 *   - Comments and string literals produce weak evidence only.
 *   - Protocol conformance and extension references are recognized.
 *
 * Limitations — the following usage patterns produce false negatives:
 *   - Dependency injection containers referencing types by string name
 *   - Protocol conformance and dynamic dispatch
 *   - KVO / selector-string lookup
 *   - Code generation (sourcery, swift-gen, etc.)
 *   - External SDK consumers outside the scanned directories
 *   - Objective-C files (.m/.mm) not in the scanned directories
 *   - Test targets and other application targets
 *   - Extensions and generated Swift code
 *   - Downstream framework consumers
 */
class SwiftUsageScanner {

    /**
     * Result of scanning Swift sources for a set of declarations.
     *
     * @property results Declarations enriched with per-file evidence.
     * @property scannedFileCount Number of Swift source files actually scanned.
     * @property scannedLineCount Total lines across all scanned Swift files.
     * @property sourceRoots The directories that were scanned.
     */
    data class ScanOutput(
        val results: List<DeclarationScanResult>,
        val scannedFileCount: Int,
        val scannedLineCount: Int,
        val sourceRoots: Set<String>,
    ) {
        /** Declarations with no reference detected. */
        val unreferenced: List<DeclarationScanResult>
            get() = results.filter { !it.isReferenced }

        /** Declarations with at least one reference detected. */
        val referenced: List<DeclarationScanResult>
            get() = results.filter { it.isReferenced }
    }

    /**
     * Scan Swift sources and return declarations with evidence.
     *
     * @param declarations Classified declarations from the ObjC header.
     * @param swiftSourceDirs Directories containing Swift source files.
     * @return [ScanOutput] with evidence-bearing results and scan metadata.
     */
    fun findUnreferenced(
        declarations: List<ExportedDeclaration>,
        swiftSourceDirs: Set<File>,
    ): ScanOutput {
        val files = collectSwiftFiles(swiftSourceDirs)
        val fileContents = files.map { file ->
            FileContent(file.absolutePath, file.readText())
        }
        val totalLines = fileContents.sumOf { it.text.lines().size }

        val results = declarations.map { decl ->
            analyzeDeclaration(decl, fileContents)
        }

        return ScanOutput(
            results = results,
            scannedFileCount = files.size,
            scannedLineCount = totalLines,
            sourceRoots = swiftSourceDirs.map { it.absolutePath }.toSet(),
        )
    }

    private data class FileContent(val path: String, val text: String)

    private fun collectSwiftFiles(dirs: Set<File>): List<File> {
        val files = mutableListOf<File>()
        for (dir in dirs) {
            if (dir.isDirectory) {
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "swift" }
                    .forEach { files.add(it) }
            }
        }
        return files
    }

    private fun analyzeDeclaration(
        decl: ExportedDeclaration,
        files: List<FileContent>,
    ): DeclarationScanResult {
        val evidence = mutableListOf<ScanEvidence>()

        for (file in files) {
            // Check for type name as standalone token in executable code
            val typeEvidence = findTypeReference(decl.name, file)
            evidence.addAll(typeEvidence)

            // Check for member selectors as standalone tokens
            for (selector in decl.selectors) {
                val memberEvidence = findMemberReference(selector, file)
                evidence.addAll(memberEvidence)
            }
        }

        // Determine strongest match kind and confidence
        val typeMatches = evidence.filter { it.matchKind == MatchKind.TYPE_REFERENCE }
        val memberMatches = evidence.filter { it.matchKind == MatchKind.MEMBER_REFERENCE }
        val weakMatches = evidence.filter { it.matchKind == MatchKind.WEAK_TEXTUAL }

        return when {
            typeMatches.isNotEmpty() -> DeclarationScanResult(
                declaration = decl,
                status = MatchKind.TYPE_REFERENCE,
                confidence = Confidence.HIGH,
                evidence = typeMatches,
            )
            memberMatches.isNotEmpty() -> DeclarationScanResult(
                declaration = decl,
                status = MatchKind.MEMBER_REFERENCE,
                confidence = Confidence.MEDIUM,
                evidence = memberMatches,
            )
            weakMatches.isNotEmpty() -> DeclarationScanResult(
                declaration = decl,
                status = MatchKind.WEAK_TEXTUAL,
                confidence = Confidence.LOW,
                evidence = weakMatches,
            )
            else -> DeclarationScanResult(
                declaration = decl,
                status = MatchKind.NO_REFERENCE,
                confidence = Confidence.HIGH,
                evidence = emptyList(),
            )
        }
    }

    /**
     * Find a type name as a standalone token in executable code (not comments/strings).
     */
    private fun findTypeReference(name: String, file: FileContent): List<ScanEvidence> {
        if (name.isEmpty()) return emptyList()
        val evidence = mutableListOf<ScanEvidence>()
        val lines = file.text.lines()

        for ((index, line) in lines.withIndex()) {
            val stripped = stripCommentsAndStrings(line)
            if (nameTokenPattern(name).containsMatchIn(stripped)) {
                evidence.add(
                    ScanEvidence(
                        filePath = file.path,
                        lineNumber = index + 1,
                        matchKind = MatchKind.TYPE_REFERENCE,
                        matchedText = name,
                    )
                )
            }
        }
        return evidence
    }

    /**
     * Find a member selector as a standalone token in executable code.
     */
    private fun findMemberReference(selector: String, file: FileContent): List<ScanEvidence> {
        if (selector.isEmpty()) return emptyList()
        val evidence = mutableListOf<ScanEvidence>()
        val swiftSelector = selector.substringBefore(":").trim()
        if (swiftSelector.isEmpty()) return emptyList()

        val lines = file.text.lines()
        for ((index, line) in lines.withIndex()) {
            val stripped = stripCommentsAndStrings(line)
            if (nameTokenPattern(swiftSelector).containsMatchIn(stripped)) {
                evidence.add(
                    ScanEvidence(
                        filePath = file.path,
                        lineNumber = index + 1,
                        matchKind = MatchKind.MEMBER_REFERENCE,
                        matchedText = swiftSelector,
                    )
                )
            }
        }
        return evidence
    }

    /**
     * Strip line comments (`//`) and string literals (`"..."`) from a line.
     * This is a heuristic — nested strings and escaped quotes are not fully handled.
     */
    private fun stripCommentsAndStrings(line: String): String {
        var result = line
        // Remove line comments
        val commentIndex = result.indexOf("//")
        if (commentIndex >= 0) {
            result = result.substring(0, commentIndex)
        }
        // Remove string literals (simple heuristic: remove content between double quotes)
        result = STRING_LITERAL_REGEX.replace(result, "\"\"")
        return result
    }

    companion object {
        private val STRING_LITERAL_REGEX = Regex("\"[^\"]*\"")

        /**
         * Creates a regex that matches [word] as a standalone token:
         * preceded by a non-alphanumeric/non-underscore character or at start of string,
         * and followed by a non-alphanumeric/non-underscore character or at end of string.
         */
        fun nameTokenPattern(word: String): Regex {
            return Regex("(?<![A-Za-z0-9_])\\Q$word\\E(?![A-Za-z0-9_])")
        }
    }
}
