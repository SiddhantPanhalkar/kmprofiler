package io.github.siddhantpanhalkar.kmprofiler.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateLinkMapTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val iosWorkspace: Property<File>

    @get:Input
    @get:Optional
    abstract val iosProject: Property<File>

    @get:Input
    @get:Optional
    abstract val iosScheme: Property<String>

    @get:OutputFile
    abstract val linkMapOutput: RegularFileProperty

    @TaskAction
    fun generate() {
        val scheme = iosScheme.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("kmprofiler: 'iosScheme' must be configured to generate a link map.")

        val workspace = iosWorkspace.orNull
        val project = iosProject.orNull

        val (targetFlag, targetFile) = when {
            workspace != null -> "-workspace" to workspace
            project != null -> "-project" to project
            else -> throw GradleException("kmprofiler: Either 'iosWorkspace' or 'iosProject' must be configured to generate a link map.")
        }

        if (!targetFile.exists()) {
            throw GradleException("kmprofiler: Configured ${targetFlag.removePrefix("-")} does not exist: ${targetFile.absolutePath}")
        }

        logger.lifecycle("Building iOS app with xcodebuild ($targetFlag ${targetFile.absolutePath}, scheme: $scheme)...")

        // 1. Build with map file generation enabled
        executeAndStream(
            listOf(
                "xcodebuild",
                targetFlag,
                targetFile.absolutePath,
                "-scheme",
                scheme,
                "-configuration",
                "Release",
                "-destination",
                "generic/platform=iOS",
                "LD_GENERATE_MAP_FILE=YES",
                "clean",
                "build"
            ),
            errorMessage = "xcodebuild clean build failed"
        )

        // 2. Fetch build settings to locate TARGET_TEMP_DIR
        val settingsOutput = executeAndCapture(
            listOf(
                "xcodebuild",
                targetFlag,
                targetFile.absolutePath,
                "-scheme",
                scheme,
                "-configuration",
                "Release",
                "-showBuildSettings"
            ),
            errorMessage = "xcodebuild -showBuildSettings failed"
        )

        // 3. Parse TARGET_TEMP_DIR
        val targetTempDirRegex = Regex("""^\s*TARGET_TEMP_DIR\s*=\s*(.+)$""")
        val targetTempDirPath = settingsOutput.lines()
            .mapNotNull { line -> targetTempDirRegex.find(line)?.groupValues?.get(1)?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: throw GradleException("kmprofiler: Could not find TARGET_TEMP_DIR in xcodebuild -showBuildSettings output.")

        val targetTempDir = File(targetTempDirPath)
        if (!targetTempDir.exists()) {
            throw GradleException("kmprofiler: TARGET_TEMP_DIR directory does not exist: $targetTempDirPath")
        }

        // 4. Find link map matching *-LinkMap-*-arm64.txt
        val linkMapPattern = Regex(".*-LinkMap-.*-arm64\\.txt")
        val linkMapFile = targetTempDir.walkTopDown()
            .filter { it.isFile && it.name.matches(linkMapPattern) }
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("kmprofiler: Link map file matching '*-LinkMap-*-arm64.txt' not found in $targetTempDirPath")

        // 5. Copy link map to task output destination
        val destination = linkMapOutput.get().asFile
        destination.parentFile?.mkdirs()
        linkMapFile.copyTo(destination, overwrite = true)

        logger.lifecycle("Link map successfully generated and copied to: ${destination.absolutePath}")
    }

    private fun executeAndStream(command: List<String>, errorMessage: String) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        // Stream output in real-time so the user knows it hasn't frozen
        process.inputStream.bufferedReader().forEachLine { line ->
            logger.lifecycle(line)
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("kmprofiler: $errorMessage with exit code $exitCode")
        }
    }

    private fun executeAndCapture(command: List<String>, errorMessage: String): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error(output)
            throw GradleException("kmprofiler: $errorMessage with exit code $exitCode:\n$output")
        }

        return output
    }
}
