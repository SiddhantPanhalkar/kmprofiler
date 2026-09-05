package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import java.util.Locale

/** Renders mapped symbol totals by object-file or inferred library name. */
class AttributionReportRenderer {

    fun render(attributions: List<LinkMapParser.SymbolAttribution>, totalBytes: Long): String {
        val byModule = attributions.groupBy { it.moduleName }
            .mapValues { (_, symbols) -> symbols.sumOf { it.sizeBytes } }
            .entries
            .sortedByDescending { it.value }

        return buildString {
            appendLine("### kmprofiler: Symbol Attribution")
            appendLine()
            appendLine("#### Module Breakdown")
            appendLine()
            appendLine("| Module | Binary Size | Symbols | % of Total |")
            appendLine("|:---|---:|---:|---:|")
            for ((module, bytes) in byModule) {
                val count = attributions.count { it.moduleName == module }
                val pct = if (totalBytes > 0) {
                    String.format(Locale.US, "%.1f%%", bytes.toDouble() / totalBytes * 100)
                } else "0.0%"
                val formattedCount = String.format(Locale.US, "%,d", count)
                appendLine("| `$module` | ${formatBytes(bytes)} | $formattedCount | $pct |")
            }
            appendLine()
            appendLine("#### Top 50 Symbols by Size")
            appendLine()
            appendLine("| Symbol | Size | Module | Category | Object File |")
            appendLine("|:---|---:|:---|:---|:---|")
            val topSymbols = attributions.take(50)
            for (attr in topSymbols) {
                val shortPath = shortenPath(attr.objectFilePath)
                val category = attr.category?.let { "`$it`" } ?: "-"
                val displaySymbol = if (attr.symbolName.length > 80) {
                    attr.symbolName.take(77) + "..."
                } else {
                    attr.symbolName
                }
                appendLine("| `$displaySymbol` | ${formatBytes(attr.sizeBytes)} | `${attr.moduleName}` | $category | `$shortPath` |")
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

    private fun shortenPath(path: String): String {
        val parts = path.split("/")
        if (parts.size <= 3) return path
        return ".../${parts.dropLast(1).last()}/${parts.last()}"
    }
}
