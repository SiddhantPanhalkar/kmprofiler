package io.github.siddhantpanhalkar.kmprofiler

import io.github.siddhantpanhalkar.kmprofiler.task.AnalyzeKmprofilerTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class KmprofilerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "kmprofiler",
            KmprofilerExtension::class.java,
        )

        extension.frameworkBaseName.convention("Shared")
        extension.exportedFrameworkCount.convention(1)
        extension.externalPrefixes.convention(emptyList())
        extension.swiftSourceDirs.setFrom(project.file("iosApp"))
        extension.allowEmptyConsumerSources.convention(false)

        val transitiveExportValue =
            project.findProperty("kotlin.native.transitiveExport")?.toString()?.toBoolean()

        project.tasks.register("analyzeKmprofiler", AnalyzeKmprofilerTask::class.java) { task ->
            task.group = "kmprofiler"
            task.description =
                "Analyses the Kotlin/Native ObjC export surface and emits a Markdown report."

            task.headerFile.set(extension.headerFile)
            task.swiftSources.setFrom(extension.swiftSourceDirs)
            task.reportOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-report.md")
            )
            task.transitiveExport.set(transitiveExportValue)
            task.staticLinkage.set(extension.isStatic)
            task.exportedFrameworkCount.set(extension.exportedFrameworkCount)
            task.externalPrefixes.set(extension.externalPrefixes)
            task.allowEmptyConsumerSources.set(extension.allowEmptyConsumerSources)
        }
    }
}
