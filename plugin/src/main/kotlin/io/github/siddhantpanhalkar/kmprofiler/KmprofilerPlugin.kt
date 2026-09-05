package io.github.siddhantpanhalkar.kmprofiler

import io.github.siddhantpanhalkar.kmprofiler.task.AnalyzeKmprofilerBinaryTask
import io.github.siddhantpanhalkar.kmprofiler.task.AnalyzeKmprofilerTask
import io.github.siddhantpanhalkar.kmprofiler.task.AttributeSymbolsTask
import io.github.siddhantpanhalkar.kmprofiler.task.CompareLinkMapTask
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
        extension.frameworkPrefix.convention(extension.frameworkBaseName)
        extension.exportedFrameworkCount.convention(1)
        extension.externalPrefixes.convention(emptyList())
        extension.swiftSourceDirs.setFrom(project.file("iosApp"))
        extension.allowEmptyConsumerSources.convention(false)
        extension.xcodeConfiguration.convention("Release")
        extension.sdkDestination.convention("generic/platform=iOS")
        extension.xcodeTimeoutMinutes.convention(30)

        val transitiveExportValue =
            project.findProperty("kotlin.native.transitiveExport")?.toString()?.toBoolean()

        // Export surface audit.
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

        // Link map generation.
        val generateTask = project.tasks.register(
            "generateKmprofilerLinkMap",
            GenerateLinkMapTask::class.java
        ) { task ->
            task.group = "kmprofiler"
            task.description =
                "Builds the iOS application with LD_GENERATE_MAP_FILE=YES and exports the link map."

            task.iosWorkspace.set(extension.iosWorkspace)
            task.iosProject.set(extension.iosProject)
            task.iosScheme.set(extension.iosScheme)
            task.xcodeConfiguration.set(extension.xcodeConfiguration)
            task.sdkDestination.set(extension.sdkDestination)
            task.architecture.set(extension.architecture)
            task.derivedDataPath.set(extension.derivedDataPath)
            task.xcconfig.set(extension.xcconfig)
            task.timeoutMinutes.set(extension.xcodeTimeoutMinutes)
            task.linkMapOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-linkmap.txt")
            )
        }

        // Mapped symbol profile.
        val profileBinaryTask = project.tasks.register(
            "profileIosBinary",
            AnalyzeKmprofilerBinaryTask::class.java
        ) { task ->
            task.group = "kmprofiler"
            task.description =
                "Analyses the iOS binary footprint from Xcode link map and emits a Markdown report."

            task.linkMapFile.set(extension.xcodeLinkMapFile)
            task.frameworkPrefix.convention(extension.frameworkPrefix)
            task.reportOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-binary-report.md")
            )
        }

        // Object-file attribution.
        val attributeSymbolsTask = project.tasks.register(
            "attributeKmprofilerSymbols",
            AttributeSymbolsTask::class.java
        ) { task ->
            task.group = "kmprofiler"
            task.description =
                "Attributes link map symbols to object files and modules."

            task.linkMapFile.set(extension.xcodeLinkMapFile)
            task.frameworkPrefix.convention(extension.frameworkPrefix)
            task.reportOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-attribution.md")
            )
        }

        // Baseline and candidate comparison.
        project.tasks.register(
            "compareKmprofilerLinkMaps",
            CompareLinkMapTask::class.java
        ) { task ->
            task.group = "kmprofiler"
            task.description =
                "Compares two link maps and reports binary size deltas."

            task.baselineLinkMap.convention(
                extension.baselineLinkMap.orElse(project.layout.buildDirectory.file("reports/kmprofiler-linkmap-baseline.txt"))
            )
            task.candidateLinkMap.convention(
                extension.candidateLinkMap.orElse(project.layout.buildDirectory.file("reports/kmprofiler-linkmap-candidate.txt"))
            )
            task.frameworkPrefix.convention(extension.frameworkPrefix)
            task.reportOutput.convention(
                project.layout.buildDirectory.file("reports/kmprofiler-comparison.md")
            )
        }

        // Run the Xcode build first when a scheme is configured.
        project.afterEvaluate {
            if (extension.iosScheme.isPresent) {
                profileBinaryTask.configure { task ->
                    task.dependsOn("generateKmprofilerLinkMap")
                    if (!extension.xcodeLinkMapFile.isPresent) {
                        task.linkMapFile.set(generateTask.flatMap { it.linkMapOutput })
                    }
                }
                attributeSymbolsTask.configure { task ->
                    task.dependsOn("generateKmprofilerLinkMap")
                    if (!extension.xcodeLinkMapFile.isPresent) {
                        task.linkMapFile.set(generateTask.flatMap { it.linkMapOutput })
                    }
                }
            }
        }
    }
}
