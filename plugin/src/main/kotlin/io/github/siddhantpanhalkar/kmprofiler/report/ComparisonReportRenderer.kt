package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import java.util.Locale

/**
 * Renders a Markdown comparison report for baseline vs candidate link maps.
 */
class ComparisonReportRenderer {

    fun render(comparison: LinkMapParser.ComparisonResult): String {
        val baseline = comparison.baseline
        val candidate = comparison.candidate

        return buildString {
            appendLine("### kmprofiler: Binary Size Comparison")
            appendLine()
            appendLine("#### Build Equivalence")
            appendLine("- **Baseline total:** ${formatBytes(baseline.totalMappedBytes)} (${baseline.symbolCount} symbols)")
            appendLine("- **Candidate total:** ${formatBytes(candidate.totalMappedBytes)} (${candidate.symbolCount} symbols)")
            appendLine("- **Delta:** ${formatBytesDelta(comparison.totalDelta)}")
            appendLine("- **Baseline coverage:** ${String.format(Locale.US, "%.1f%%", baseline.coveragePercentage)}")
            appendLine("- **Candidate coverage:** ${String.format(Locale.US, "%.1f%%", candidate.coveragePercentage)}")
            if (comparison.equivalenceWarnings.isNotEmpty()) {
                appendLine()
                for (warning in comparison.equivalenceWarnings) {
                    appendLine("> **Warning:** $warning")
                }
            }
            appendLine()
            appendLine("#### Category Deltas")
            appendLine("| Category | Baseline | Candidate | Delta | Delta % |")
            appendLine("|---|---|---|---|---|")
            val sortedDeltas = comparison.categoryDeltas.entries.sortedByDescending { it.value.deltaBytes }
            for ((category, delta) in sortedDeltas) {
                val deltaStr = formatBytesDelta(delta.deltaBytes)
                val pctStr = if (delta.baselineBytes > 0) {
                    String.format(Locale.US, "%+.1f%%", delta.deltaPercentage)
                } else {
                    "n/a"
                }
                appendLine("| $category | ${formatBytes(delta.baselineBytes)} | ${formatBytes(delta.candidateBytes)} | $deltaStr | $pctStr |")
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatBytesDelta(delta: Long): String {
        val sign = if (delta > 0) "+" else if (delta < 0) "" else ""
        return "$sign${formatBytes(kotlin.math.abs(delta))}"
    }
}
