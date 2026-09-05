package io.github.siddhantpanhalkar.kmprofiler.task

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.Locale

abstract class AnalyzeKmprofilerBinaryTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    abstract val linkMapFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val frameworkPrefix: Property<String>

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun analyze() {
        if (!linkMapFile.isPresent) {
            throw GradleException(
                "kmprofiler: Link map file is not configured. " +
                        "Specify 'xcodeLinkMapFile' in kmprofiler extension or configure 'iosScheme' to generate it automatically."
            )
        }

        val mapFile = linkMapFile.get().asFile
        if (!mapFile.exists() || !mapFile.isFile) {
            throw GradleException("kmprofiler: Link map file does not exist: ${mapFile.absolutePath}")
        }

        val prefix = frameworkPrefix.orNull?.takeIf { it.isNotBlank() } ?: "Shared"
        val result = LinkMapParser().parse(mapFile, prefix, collectSymbols = false)
        if (!result.isValid || result.symbolCount == 0L) {
            throw GradleException("kmprofiler: Link map file does not contain a valid '# Symbols:' section or has 0 live symbols: ${mapFile.absolutePath}")
        }

        val markdown = buildString {
            appendLine("### kmprofiler: iOS Binary Size Breakdown")
            appendLine()
            appendLine("#### Summary")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|:---|---:|")
            appendLine("| Total Symbols | ${String.format(Locale.US, "%,d", result.symbolCount)} |")
            appendLine(
                "| Classified Symbols | ${
                    String.format(
                        Locale.US,
                        "%,d",
                        result.classifiedSymbolCount
                    )
                } |"
            )
            appendLine("| Total Mapped Symbol Size | ${formatBytes(result.totalMappedBytes)} |")
            appendLine(
                "| Total Classified Mapped Size | ${formatBytes(result.classifiedBytes)} (${
                    String.format(
                        Locale.US,
                        "%.1f%%",
                        result.coveragePercentage
                    )
                }) |"
            )
            appendLine(
                "| Unclassified Mapped Symbols | ${formatBytes(result.unclassifiedBytes)} (${
                    String.format(
                        Locale.US,
                        "%.1f%%",
                        100.0 - result.coveragePercentage
                    )
                }) |"
            )
            appendLine()
            appendLine("#### Package / Category Breakdown")
            appendLine()
            appendLine("| Package / Category | Binary Size | % of Total |")
            appendLine("|:---|---:|---:|")
            for ((category, sizeBytes) in result.categories) {
                val pct = if (result.totalMappedBytes > 0) {
                    String.format(
                        Locale.US,
                        "%.1f%%",
                        sizeBytes.toDouble() / result.totalMappedBytes * 100
                    )
                } else "0.0%"
                appendLine("| `$category` | ${formatBytes(sizeBytes)} | $pct |")
            }
        }

        val destination = reportOutput.get().asFile
        destination.parentFile?.mkdirs()
        destination.writeText(markdown)

        val top3 = result.categories.entries.take(3)
        logger.lifecycle("\nkmprofiler: iOS Binary Footprint Breakdown (Top ${top3.size}):")
        if (top3.isEmpty()) {
            logger.lifecycle("  No matching symbols found in link map.")
        } else {
            top3.forEachIndexed { index, (category, bytes) ->
                logger.lifecycle("  ${index + 1}. $category: ${formatBytes(bytes)}")
            }
        }
        logger.lifecycle(
            "  Classified coverage: ${
                String.format(
                    Locale.US,
                    "%.1f%%",
                    result.coveragePercentage
                )
            }"
        )
        logger.lifecycle("  Full binary report written to: ${destination.absolutePath}")
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
