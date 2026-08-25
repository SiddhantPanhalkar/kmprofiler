package io.github.siddhantpanhalkar.kmprofiler

import io.github.siddhantpanhalkar.kmprofiler.task.AnalyzeKmprofilerTask
import io.github.siddhantpanhalkar.kmprofiler.task.GenerateLinkMapTask
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

        project.tasks.register("generateKmprofilerLinkMap", GenerateLinkMapTask::class.java) { task ->
            task.group = "kmprofiler"
            task.description =
                "Builds the iOS application with LD_GENERATE_MAP_FILE=YES and exports the link map."

            task.iosWorkspace.set(extension.iosWorkspace)
            task.iosProject.set(extension.iosProject)
            task.iosScheme.set(extension.iosScheme)
            task.linkMapOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-linkmap.txt")
            )
        }
    }
}

