package io.github.siddhantpanhalkar.kmprofiler.functional

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Regression fixture test derived from the framed-app reference project.
 *
 * This test verifies that the plugin produces deterministic output for a
 * representative KMP header containing: Compose top-level declarations,
 * Koin DI, RevenueCat/Ktor transitive types, sealed interfaces, public data
 * models, expect/actual, and Swift bridge entry points.
 *
 * The golden report at src/test/resources/fixtures/regression/golden-report.md
 * is the committed baseline. Every recommended change in the golden workflow
 * must compile for Android and physical-device iOS ARM64 framework targets.
 */
class RegressionFixtureTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var headerFile: File
    private lateinit var swiftDir: File

    @BeforeEach
    fun setup() {
        // Copy fixture header
        val fixtureDir = File(javaClass.classLoader.getResource("fixtures/regression")!!.toURI())
        headerFile = File(testProjectDir, "Shared.h").apply {
            writeText(File(fixtureDir, "Shared.h").readText())
        }

        // Copy fixture Swift files
        swiftDir = File(testProjectDir, "iosApp").apply {
            mkdirs()
        }
        File(fixtureDir, "swift").copyRecursively(swiftDir)

        // Write build file
        File(testProjectDir, "settings.gradle.kts").writeText(
            """rootProject.name = "regression-test""""
        )
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                headerFile.set(file("${headerFile.absolutePath.replace("\\", "/")}"))
                swiftSourceDirs.setFrom(file("${swiftDir.absolutePath.replace("\\", "/")}"))
                isStatic.set(true)
                exportedFrameworkCount.set(1)
            }
        """.trimIndent()
        )
    }

    @Test
    fun `regression fixture produces deterministic report structure`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .build()

        assertThat(result.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        assertThat(report).exists()
        val text = report.readText()

        // Verify report structure
        assertThat(text).contains("### kmprofiler: iOS Export Profile")
        assertThat(text).contains("#### Provenance")
        assertThat(text).contains("#### Config")

        // Verify no harmful remediation text
        assertThat(text).doesNotContain("@file:HiddenFromObjC")

        // Verify correct terminology
        assertThat(text).contains("review candidates")
        assertThat(text).contains("Decision tree")
        assertThat(text).contains("configured in kmprofiler")
        assertThat(text).contains("declared to the profiler")
        assertThat(text).contains("Heuristic-based")

        // Verify evidence columns present
        assertThat(text).contains("Confidence")
        assertThat(text).contains("Remediation")

        // Verify provenance
        assertThat(text).contains("**Swift files scanned:** 2")
        assertThat(text).contains("**Ownership analysis:**")

        // Verify sections exist (with actual counts from the fixture)
        assertThat(text).contains("#### Your code - review candidates")
        assertThat(text).contains("#### Kotlin file facades - review candidates")
        assertThat(text).contains("#### Unresolved ownership")

        // Verify config lint
        assertThat(text).contains("`isStatic = true`")
    }

    @Test
    fun `regression fixture correctly identifies unreferenced declarations`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .build()

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        val text = report.readText()

        // These types are directly referenced by name in Swift (ContentView.swift, ProfileScreen.swift)
        assertThat(text).doesNotContain("| `SharedCameraRegistry`")
        assertThat(text).doesNotContain("| `SharedCameraCaptureBridge`")
        assertThat(text).doesNotContain("| `SharedFilterApplyBridge`")
        assertThat(text).doesNotContain("| `SharedHardwareShutterHandler`")
        assertThat(text).doesNotContain("| `SharedZoomBridge`")
        assertThat(text).doesNotContain("| `SharedExposureBridge`")
        assertThat(text).doesNotContain("| `SharedFlashBridge`")

        // These types are NOT directly referenced in Swift (accessed via properties, not by name)
        assertThat(text).contains("`SharedCameraState`")
        assertThat(text).contains("`SharedReviewState`")
        assertThat(text).contains("`SharedFilterRecipe`")
        assertThat(text).contains("`SharedCurvePoint`")
    }

    @Test
    fun `regression fixture classifies external libraries correctly`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .build()

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        val text = report.readText()

        // External library types with underscore pattern should be in unresolved ownership section
        assertThat(text).contains("Ktor_client_coreHttpClient")
        assertThat(text).contains("Koin_coreModule")
        assertThat(text).contains("Koin_coreScope")
    }

    @Test
    fun `regression fixture classifies file facades correctly`() {
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .build()

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        val text = report.readText()

        // Kt facades should be in the file facades section
        assertThat(text).contains("SharedColorKt")
        assertThat(text).contains("SharedDimensKt")
        assertThat(text).contains("SharedDateUtilsKt")
    }
}
