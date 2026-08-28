package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import java.util.Locale

/**
 * Renders a Markdown attribution report mapping symbols to object files and modules.
 */
class AttributionReportRenderer {

    fun render(attributions: List<LinkMapParser.SymbolAttribution>, totalBytes: Long): String {
        // Group by module
        val byModule = attributions.groupBy { it.moduleName }
            .mapValues { (_, symbols) -> symbols.sumOf { it.sizeBytes } }
            .entries
            .sortedByDescending { it.value }

        return buildString {
            appendLine("### kmprofiler: Symbol Attribution")
            appendLine()
            appendLine("#### Module Breakdown")
            appendLine("| Module | Bytes | Symbols |")
            appendLine("|---|---|---|")
            for ((module, bytes) in byModule) {
                val count = attributions.count { it.moduleName == module }
                appendLine("| $module | ${formatBytes(bytes)} | $count |")
            }
            appendLine()
            appendLine("#### Top Symbols by Size")
            appendLine("| Symbol | Size | Object File | Module | Category |")
            appendLine("|---|---|---|---|---|")
            val topSymbols = attributions.take(50)
            for (attr in topSymbols) {
                val shortPath = shortenPath(attr.objectFilePath)
                val category = attr.category ?: "—"
                appendLine("| `${attr.symbolName}` | ${formatBytes(attr.sizeBytes)} | $shortPath | ${attr.moduleName} | $category |")
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
