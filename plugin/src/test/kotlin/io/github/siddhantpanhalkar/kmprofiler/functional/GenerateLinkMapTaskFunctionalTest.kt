package io.github.siddhantpanhalkar.kmprofiler.functional

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateLinkMapTaskFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "functional-test-linkmap"
            """.trimIndent()
        )
    }

    @Test
    fun `generateKmprofilerLinkMap fails when iosScheme is missing`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                iosWorkspace.set(file("App.xcworkspace"))
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("generateKmprofilerLinkMap")
            .buildAndFail()

        assertThat(result.output).contains("kmprofiler: 'iosScheme' must be configured to generate a link map.")
    }

    @Test
    fun `generateKmprofilerLinkMap fails when neither workspace nor project is configured`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                iosScheme.set("iosApp")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("generateKmprofilerLinkMap")
            .buildAndFail()

        assertThat(result.output).contains("kmprofiler: Either 'iosWorkspace' or 'iosProject' must be configured to generate a link map.")
    }

    @Test
    fun `generateKmprofilerLinkMap fails when configured workspace does not exist`() {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                iosWorkspace.set(file("NonExistent.xcworkspace"))
                iosScheme.set("iosApp")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("generateKmprofilerLinkMap")
            .buildAndFail()

        assertThat(result.output).contains("kmprofiler: Configured workspace does not exist:")
    }
}
