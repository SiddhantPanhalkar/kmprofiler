package io.github.siddhantpanhalkar.kmprofiler.task

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import io.github.siddhantpanhalkar.kmprofiler.report.AttributionReportRenderer
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

abstract class AttributeSymbolsTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    abstract val linkMapFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val frameworkPrefix: Property<String>

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun attribute() {
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
        val result = LinkMapParser().parse(mapFile, prefix)
        val attributions = LinkMapParser().attributeSymbols(result)

        val markdown = AttributionReportRenderer().render(attributions, result.totalMappedBytes)

        val destination = reportOutput.get().asFile
        destination.parentFile?.mkdirs()
        destination.writeText(markdown)

        val modules = attributions.groupBy { it.moduleName }
        logger.lifecycle("\nkmprofiler: Symbol Attribution (${attributions.size} symbols, ${modules.size} modules)")
        val topModules = modules.entries
            .map { it.key to it.value.sumOf { s -> s.sizeBytes } }
            .sortedByDescending { it.second }
            .take(5)
        for ((module, bytes) in topModules) {
            logger.lifecycle("  $module: ${formatBytes(bytes)}")
        }
        logger.lifecycle("  Full attribution report: ${destination.absolutePath}")
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
