package io.github.siddhantpanhalkar.kmprofiler.functional

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnalyzeKmprofilerBinaryTaskFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var linkMapFile: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "functional-test-binary"
            """.trimIndent()
        )

        linkMapFile = File(testProjectDir, "App-LinkMap-normal-arm64.txt").apply {
            writeText(
                """
                # Path: /Users/test/Build/Products/Release-iphoneos/App.app/App
                # Arch: arm64
                # Object files:
                [  0] linker synthesized
                [  1] /Users/test/Shared.framework/Shared
                [  2] /Users/test/App.o

                # Sections:
                # Address	Size    	Segment	Section
                0x100004000	0x00001000	__TEXT	__text

                # Symbols:
                # Address	Size    	File  Name
                0x100004000	0x00000400	[  1]	_kfun:com.example.service#fetchUser(){}
                0x100004400	0x00000800	[  1]	_kclass:io.ktor.client.HttpClient
                0x100004C00	0x00000200	[  2]	_OBJC_CLASS_${'$'}_SharedUserRepository
                """.trimIndent()
            )
        }
    }

    @Test
    fun `profileIosBinary succeeds with configured xcodeLinkMapFile and writes report`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                xcodeLinkMapFile.set(file("${linkMapFile.absolutePath.replace("\\", "/")}"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("profileIosBinary", "--stacktrace")
            .forwardOutput()
            .build()

        assertThat(result.task(":profileIosBinary")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val report = File(testProjectDir, "build/reports/kmprofiler-binary-report.md")
        assertThat(report).exists()
        val text = report.readText()
        assertThat(text).contains("| Package / Category | Binary Size |")
        assertThat(text).contains("io.ktor")
        assertThat(text).contains("2.00 KB")
        assertThat(text).contains("com.example")
        assertThat(text).contains("1.00 KB")
        assertThat(text).contains("[iOS Export Surface]")
        assertThat(text).contains("512 B")

        assertThat(result.output).contains("iOS Binary Footprint Breakdown")
        assertThat(result.output).contains("io.ktor: 2.00 KB")
    }

    @Test
    fun `profileIosBinary fails when linkMapFile is not configured`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("profileIosBinary")
            .buildAndFail()

        assertThat(result.output).contains("kmprofiler: Link map file is not configured.")
    }

    @Test
    fun `profileIosBinary fails when link map file does not exist`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                xcodeLinkMapFile.set(file("non-existent-linkmap.txt"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("profileIosBinary")
            .buildAndFail()

        assertThat(result.output).contains("which doesn't exist.")
    }

    @Test
    fun `profileIosBinary honors custom frameworkPrefix`() {
        val customMapFile = File(testProjectDir, "Custom-LinkMap.txt").apply {
            writeText(
                """
                # Symbols:
                # Address	Size    	File  Name
                0x100004000	0x00000100	[  1]	_OBJC_CLASS_${'$'}_CustomAppCore
                """.trimIndent()
            )
        }

        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                frameworkPrefix.set("Custom")
                xcodeLinkMapFile.set(file("${customMapFile.absolutePath.replace("\\", "/")}"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("profileIosBinary")
            .build()

        assertThat(result.task(":profileIosBinary")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val report = File(testProjectDir, "build/reports/kmprofiler-binary-report.md")
        val text = report.readText()
        assertThat(text).contains("[iOS Export Surface]")
    }

    @Test
    fun `profileIosBinary depends on generateKmprofilerLinkMap when iosScheme is configured`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                iosScheme.set("iosApp")
                iosProject.set(file("App.xcodeproj"))
            }
            """.trimIndent()
        )

        // Running profileIosBinary dry-run should show generateKmprofilerLinkMap scheduled before profileIosBinary
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("profileIosBinary", "--dry-run")
            .build()

        assertThat(result.output).contains(":generateKmprofilerLinkMap SKIPPED")
        assertThat(result.output).contains(":profileIosBinary SKIPPED")
    }
}
