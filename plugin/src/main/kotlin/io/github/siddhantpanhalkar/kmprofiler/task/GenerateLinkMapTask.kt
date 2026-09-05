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
import java.util.concurrent.TimeUnit

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

    @get:Input
    @get:Optional
    abstract val xcodeConfiguration: Property<String>

    @get:Input
    @get:Optional
    abstract val sdkDestination: Property<String>

    @get:Input
    @get:Optional
    abstract val architecture: Property<String>

    @get:Input
    @get:Optional
    abstract val derivedDataPath: Property<File>

    @get:Input
    @get:Optional
    abstract val xcconfig: Property<File>

    @get:Input
    @get:Optional
    abstract val timeoutMinutes: Property<Int>

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
            else -> throw GradleException("kmprofiler: Either 'iosWorkspace' or 'iosProject' must be configured.")
        }

        if (!targetFile.exists()) {
            throw GradleException("kmprofiler: Configured ${targetFlag.removePrefix("-")} does not exist: ${targetFile.absolutePath}")
        }

        val configuration = xcodeConfiguration.orNull?.takeIf { it.isNotBlank() } ?: "Release"
        val destination =
            sdkDestination.orNull?.takeIf { it.isNotBlank() } ?: "generic/platform=iOS"
        val timeout = timeoutMinutes.orNull?.toLong() ?: 30L

        logger.lifecycle("Building iOS app with xcodebuild ($targetFlag ${targetFile.absolutePath}, scheme: $scheme, configuration: $configuration)...")

        val buildArgs = mutableListOf(
            "xcodebuild",
            targetFlag,
            targetFile.absolutePath,
            "-scheme",
            scheme,
            "-configuration",
            configuration,
            "-destination",
            destination,
            "LD_GENERATE_MAP_FILE=YES",
        )

        architecture.orNull?.takeIf { it.isNotBlank() }?.let { arch ->
            buildArgs.addAll(listOf("-arch", arch))
        }
        derivedDataPath.orNull?.let { dd ->
            buildArgs.addAll(listOf("-derivedDataPath", dd.absolutePath))
        }
        xcconfig.orNull?.let { xc ->
            buildArgs.addAll(listOf("-xcconfig", xc.absolutePath))
        }

        buildArgs.addAll(listOf("clean", "build"))

        executeProcess(buildArgs, "xcodebuild clean build", timeoutMinutes = timeout)

        val settingsArgs = mutableListOf(
            "xcodebuild",
            targetFlag,
            targetFile.absolutePath,
            "-scheme",
            scheme,
            "-configuration",
            configuration,
            "-destination",
            destination,
        )
        architecture.orNull?.takeIf { it.isNotBlank() }?.let { arch ->
            settingsArgs.addAll(listOf("-arch", arch))
        }
        derivedDataPath.orNull?.let { dd ->
            settingsArgs.addAll(listOf("-derivedDataPath", dd.absolutePath))
        }
        xcconfig.orNull?.let { xc ->
            settingsArgs.addAll(listOf("-xcconfig", xc.absolutePath))
        }
        settingsArgs.add("-showBuildSettings")

        val settingsOutput =
            executeProcessCapture(settingsArgs, "xcodebuild -showBuildSettings", timeoutMinutes = 5)

        val targetTempDirPath = extractSetting(settingsOutput, "TARGET_TEMP_DIR")
        val resolvedArch = resolveArchitecture(architecture.orNull, settingsOutput)
        val linkMapFile = resolveLinkMapFile(settingsOutput, architecture.orNull)
            ?: throw GradleException("kmprofiler: Link map file not found in $targetTempDirPath for architecture $resolvedArch. Ensure LD_GENERATE_MAP_FILE=YES is set.")

        val output = linkMapOutput.get().asFile
        output.parentFile?.mkdirs()
        linkMapFile.copyTo(output, overwrite = true)

        logger.lifecycle("Link map successfully generated and copied to: ${output.absolutePath}")
    }


    companion object {
        fun extractSetting(settingsOutput: String, settingName: String): String? {
            val regex = Regex("""^\s*${Regex.escape(settingName)}\s*=\s*(.+)$""")
            return settingsOutput.lines()
                .mapNotNull { line -> regex.find(line)?.groupValues?.get(1)?.trim() }
                .firstOrNull { it.isNotEmpty() }
        }

        fun resolveArchitecture(configuredArch: String?, settingsOutput: String): String {
            return configuredArch?.takeIf { it.isNotBlank() && it != "undefined_arch" }
                ?: extractSetting(settingsOutput, "ARCHS")?.split("""\s+""".toRegex())
                    ?.firstOrNull { it.isNotBlank() && it != "undefined_arch" }
                ?: extractSetting(
                    settingsOutput,
                    "NATIVE_ARCH_ACTUAL"
                )?.takeIf { it.isNotBlank() && it != "undefined_arch" }
                ?: extractSetting(
                    settingsOutput,
                    "CURRENT_ARCH"
                )?.takeIf { it.isNotBlank() && it != "undefined_arch" }
                ?: "arm64"
        }

        fun resolveLinkMapFile(settingsOutput: String, configuredArch: String?): File? {
            val ldMapFilePath = extractSetting(settingsOutput, "LD_MAP_FILE_PATH")
            if (ldMapFilePath != null && !ldMapFilePath.contains("undefined_arch")) {
                val directFile = File(ldMapFilePath)
                if (directFile.exists() && directFile.isFile) {
                    return directFile
                }
            }

            val targetTempDirPath = extractSetting(settingsOutput, "TARGET_TEMP_DIR") ?: return null
            val targetTempDir = File(targetTempDirPath)
            if (!targetTempDir.exists() || !targetTempDir.isDirectory) return null

            val resolvedArch = resolveArchitecture(configuredArch, settingsOutput)
            val linkMapPattern = Regex(".*-LinkMap-.*-$resolvedArch\\.txt")
            return targetTempDir.walkTopDown()
                .filter { it.isFile && it.name.matches(linkMapPattern) }
                .maxByOrNull { it.lastModified() }
        }
    }


    private fun executeProcess(
        command: List<String>,
        description: String,
        timeoutMinutes: Long = 30L,
    ) {
        logger.lifecycle("  Running: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val outputThread = Thread {
            process.inputStream.bufferedReader().forEachLine { line ->
                logger.info(line)
            }
        }
        outputThread.isDaemon = true
        outputThread.start()

        val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!completed) {
            process.destroyForcibly()
            throw GradleException("kmprofiler: $description timed out after $timeoutMinutes minutes")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            throw GradleException("kmprofiler: $description failed with exit code $exitCode")
        }
    }

    private fun executeProcessCapture(
        command: List<String>,
        description: String,
        timeoutMinutes: Long = 5L,
    ): String {
        logger.lifecycle("  Running: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val completed = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw GradleException("kmprofiler: $description timed out after $timeoutMinutes minutes")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            logger.error(output)
            throw GradleException("kmprofiler: $description failed with exit code $exitCode:\n$output")
        }

        return output
    }
}
