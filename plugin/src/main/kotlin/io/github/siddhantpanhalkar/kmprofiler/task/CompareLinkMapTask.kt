package io.github.siddhantpanhalkar.kmprofiler.task

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import io.github.siddhantpanhalkar.kmprofiler.report.ComparisonReportRenderer
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

abstract class CompareLinkMapTask : DefaultTask() {

    @get:InputFile
    abstract val baselineLinkMap: RegularFileProperty

    @get:InputFile
    abstract val candidateLinkMap: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val frameworkPrefix: Property<String>

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun compare() {
        val baselineFile = baselineLinkMap.get().asFile
        val candidateFile = candidateLinkMap.get().asFile

        if (!baselineFile.exists()) {
            throw GradleException("kmprofiler: Baseline link map does not exist: ${baselineFile.absolutePath}")
        }
        if (!candidateFile.exists()) {
            throw GradleException("kmprofiler: Candidate link map does not exist: ${candidateFile.absolutePath}")
        }

        val prefix = frameworkPrefix.orNull?.takeIf { it.isNotBlank() } ?: "Shared"
        val comparison = try {
            LinkMapParser().compare(baselineFile, candidateFile, prefix)
        } catch (e: IllegalArgumentException) {
            throw GradleException(e.message ?: "Comparison failed", e)
        }
        val markdown = ComparisonReportRenderer().render(comparison)


        val destination = reportOutput.get().asFile
        destination.parentFile?.mkdirs()
        destination.writeText(markdown)

        logger.lifecycle("\nkmprofiler: Binary Size Comparison")
        logger.lifecycle("  Baseline: ${formatBytes(comparison.baseline.totalMappedBytes)}")
        logger.lifecycle("  Candidate: ${formatBytes(comparison.candidate.totalMappedBytes)}")
        logger.lifecycle("  Delta: ${formatBytesDelta(comparison.totalDelta)}")
        for (warning in comparison.equivalenceWarnings) {
            logger.lifecycle("  WARNING: $warning")
        }
        logger.lifecycle("  Full comparison report: ${destination.absolutePath}")
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatBytesDelta(delta: Long): String {
        val sign = if (delta > 0) "+" else if (delta < 0) "-" else ""
        return "$sign${formatBytes(kotlin.math.abs(delta))}"
    }
}
