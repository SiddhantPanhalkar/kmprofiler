package io.github.siddhantpanhalkar.kmprofiler.functional

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnalyzeKmprofilerTaskFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    private lateinit var headerFile: File
    private lateinit var swiftDir: File

    @BeforeEach
    fun setup() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "functional-test"
        """.trimIndent()
        )

        headerFile = File(testProjectDir, "Shared.h").apply {
            writeText(
                """
                @interface Foo : NSObject
                - (void)doSomething;
                @end
                @interface Bar : NSObject
                - (void)doOther;
                @end
            """.trimIndent()
            )
        }

        swiftDir = File(testProjectDir, "iosApp").apply {
            mkdirs()
            File(this, "ContentView.swift").writeText(
                """
                import Foundation
                func useFoo(foo: Foo) {
                    foo.doSomething()
                }
            """.trimIndent()
            )
        }
    }

    private fun writeBuildFile(swiftSourcePath: String, extraConfig: String = "") {
        File(testProjectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.siddhantpanhalkar.kmprofiler")
            }

            kmprofiler {
                headerFile.set(file("${headerFile.absolutePath.replace("\\", "/")}"))
                swiftSourceDirs.setFrom(file("$swiftSourcePath"))
                $extraConfig
            }
        """.trimIndent()
        )
    }

    @Test
    fun `analyzeKmprofiler task succeeds and writes report`() {
        writeBuildFile(swiftDir.absolutePath.replace("\\", "/"))

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .forwardOutput()
            .build()

        assertThat(result.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        assertThat(report).exists()
        val text = report.readText()
        assertThat(text).contains("Bar")
        assertThat(text).doesNotContain("Foo")
        assertThat(text).doesNotContain("@file:HiddenFromObjC")
        assertThat(text).contains("Decision tree for each candidate")
        assertThat(text).contains("#### Provenance")
    }

    @Test
    fun `analyzeKmprofiler task is incremental and up to date on second run`() {
        writeBuildFile(swiftDir.absolutePath.replace("\\", "/"))

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler")
            .build()

        val second = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler")
            .build()

        assertThat(second.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    }

    @Test
    fun `analyzeKmprofiler fails when no Swift files are discovered`() {
        val emptySwiftDir = File(testProjectDir, "emptyApp").apply { mkdirs() }
        writeBuildFile(emptySwiftDir.absolutePath.replace("\\", "/"))

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .buildAndFail()

        assertThat(result.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("No Swift source files found")
        assertThat(result.output).contains("allowEmptyConsumerSources")
    }

    @Test
    fun `analyzeKmprofiler succeeds with allowEmptyConsumerSources when no Swift files`() {
        val emptySwiftDir = File(testProjectDir, "emptyApp").apply { mkdirs() }
        writeBuildFile(
            emptySwiftDir.absolutePath.replace("\\", "/"),
            "allowEmptyConsumerSources.set(true)"
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler", "--stacktrace")
            .build()

        assertThat(result.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        val report = File(testProjectDir, "build/reports/kmprofiler-report.md")
        assertThat(report).exists()
        val text = report.readText()
        // All declarations should be candidates when no Swift files scanned
        assertThat(text).contains("Foo")
        assertThat(text).contains("Bar")
    }
}
