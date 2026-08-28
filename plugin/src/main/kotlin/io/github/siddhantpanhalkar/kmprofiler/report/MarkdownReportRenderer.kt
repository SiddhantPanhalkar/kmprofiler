package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.model.ConfigLintResult
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ScanResult
import java.time.Instant

/** Renders the v1 export audit report without making reachability or size claims. */
object MarkdownReportRenderer {

    fun render(result: ScanResult): String = buildString {
        appendLine("### kmprofiler iOS Export Audit")
        appendLine()
        appendLine("**Export surface:** ${result.totalExported} declarations found in the generated Objective-C header.")

        val candidateCount = result.reviewCandidates.size +
                result.kotlinFileFacadeCandidates.size + result.likelyExternalCandidates.size
        if (candidateCount == 0) {
            appendLine("**No review candidates found in the configured Swift source scan.**")
        } else {
            appendLine("**No standalone declaration-name token found for $candidateCount declarations in the configured Swift sources.**")
        }

        appendLine()
        appendLine("> Review candidates are not unused-code findings and are not safe-to-hide recommendations.")
        appendLine("> This scan only looks for standalone declaration names in configured Swift source files.")
        appendLine("> It excludes comments and string literals, and does not treat a member name such as `start()`")
        appendLine("> as a reference because the receiver type cannot be established from a text search.")
        appendLine("> It does not analyze Kotlin call graphs, public API relationships, Objective-C source, generated")
        appendLine("> code, tests outside the configured roots, or downstream consumers.")

        appendLine()
        appendLine("#### Provenance")
        if (result.headerPath.isNotBlank()) {
            appendLine("- Header: `${result.headerPath}`")
        }
        if (result.headerTimestamp > 0) {
            appendLine("- Header modified: ${Instant.ofEpochMilli(result.headerTimestamp)}")
        }
        appendLine("- Swift files scanned: ${result.scannedFileCount}")
        appendLine("- Swift lines scanned: ${result.scannedLineCount}")
        appendLine("- Evidence: standalone declaration-name tokens in executable Swift source only")

        if (result.reviewCandidates.isNotEmpty()) {
            appendLine()
            appendLine("#### App-code review candidates (${result.reviewCandidates.size})")
            appendLine()
            appendLine("These names did not occur as standalone tokens in the configured Swift sources.")
            renderDeclarationTable(this, result.reviewCandidates, includeKind = true)
            renderManualReviewGuidance(this)
        }

        if (result.kotlinFileFacadeCandidates.isNotEmpty()) {
            appendLine()
            appendLine("#### Kotlin file facade review candidates (${result.kotlinFileFacadeCandidates.size})")
            appendLine()
            appendLine("`*Kt` entries are Kotlin/Native facades for top-level declarations. Inspect the individual")
            appendLine("Kotlin functions or properties behind a facade. `@HiddenFromObjC` is valid on eligible")
            appendLine("classes, functions, and properties. It is not a file annotation.")
            renderDeclarationTable(this, result.kotlinFileFacadeCandidates, includeKind = false)
        }

        if (result.likelyExternalCandidates.isNotEmpty()) {
            appendLine()
            appendLine("#### Ownership needs review (${result.likelyExternalCandidates.size})")
            appendLine()
            appendLine("These entries match a configured prefix or a Kotlin/Native naming pattern. That is a")
            appendLine("classification hint, not evidence that they belong to a dependency. Confirm ownership")
            appendLine("and the framework export configuration before changing code or dependencies.")
            renderDeclarationTable(this, result.likelyExternalCandidates, includeKind = true)
        }

        appendLine()
        appendLine("#### Config values supplied to kmprofiler")
        appendConfigLint(result.configLint)
    }

    private fun renderDeclarationTable(
        output: StringBuilder,
        candidates: List<DeclarationScanResult>,
        includeKind: Boolean,
    ) {
        output.appendLine()
        if (includeKind) {
            output.appendLine("| Declaration | Kind | Members | Scan result |")
            output.appendLine("|---|---|---:|---|")
        } else {
            output.appendLine("| Declaration | Members | Scan result |")
            output.appendLine("|---|---:|---|")
        }
        candidates.sortedByDescending { it.memberCount }.forEach { candidate ->
            val members =
                if (candidate.memberCount == 0) "0 (empty)" else candidate.memberCount.toString()
            if (includeKind) {
                output.appendLine("| `${candidate.name}` | ${candidate.kind.label()} | $members | no type token found |")
            } else {
                output.appendLine("| `${candidate.name}` | $members | no type token found |")
            }
        }
    }

    private fun renderManualReviewGuidance(output: StringBuilder) {
        output.appendLine()
        output.appendLine("Before changing a candidate:")
        output.appendLine()
        output.appendLine("1. Check whether it is intentionally public Kotlin API.")
        output.appendLine("2. Check every public or protected declaration that accepts, returns, inherits, or exposes it.")
        output.appendLine("3. Use `internal` only when the declaration can remain visible to all required Kotlin source sets.")
        output.appendLine("4. Use `private` only when its required Kotlin scope permits it.")
        output.appendLine("5. Consider declaration-level `@HiddenFromObjC` only when Kotlin visibility must remain public and the declaration is eligible.")
        output.appendLine("6. Compile all affected Kotlin and iOS targets, then re-link and re-run the audit.")
    }

    private fun StringBuilder.appendConfigLint(config: ConfigLintResult) {
        when (config.transitiveExport) {
            true -> appendLine("- `transitiveExport = true` was supplied to kmprofiler. Review whether transitive dependencies should be exported.")
            false -> appendLine("- `transitiveExport = false` was supplied to kmprofiler.")
            null -> appendLine("- `transitiveExport` was not supplied to kmprofiler. Confirm the framework setting in the KMP build.")
        }
        when (config.isStatic) {
            true -> appendLine("- `isStatic = true` was supplied to kmprofiler. This report does not measure final app size.")
            false -> appendLine("- `isStatic = false` was supplied to kmprofiler. This report does not measure final app size.")
            null -> appendLine("- `isStatic` was not supplied to kmprofiler. Confirm the framework setting in the KMP build.")
        }
        appendLine("- Exported framework count supplied to kmprofiler: ${config.exportedFrameworkCount}")
    }

    private fun DeclarationKind.label() = when (this) {
        DeclarationKind.CLASS -> "class"
        DeclarationKind.PROTOCOL -> "protocol"
        DeclarationKind.INTERFACE -> "interface"
    }
}
