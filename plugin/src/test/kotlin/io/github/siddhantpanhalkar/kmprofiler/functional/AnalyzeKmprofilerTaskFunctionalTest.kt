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

    private lateinit var buildFile: File
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

        buildFile = File(testProjectDir, "build.gradle.kts").apply {
            writeText(
                """
                plugins {
                    id("io.github.siddhantpanhalkar.kmprofiler")
                }

                kmprofiler {
                    headerFile.set(file("${headerFile.absolutePath.replace("\\", "/")}"))
                    swiftSourceDirs.setFrom(file("${swiftDir.absolutePath.replace("\\", "/")}"))
                }
            """.trimIndent()
            )
        }
    }

    @Test
    fun `analyzeKmprofiler task succeeds and writes report`() {
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
    }

    @Test
    fun `analyzeKmprofiler task is incremental and up to date on second run`() {
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
    fun `analyzeKmprofiler fails when configured swift sources are empty by default`() {
        swiftDir.listFiles()?.forEach { it.delete() }

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler")
            .buildAndFail()

        assertThat(result.output).contains("No Swift source files were found")
        assertThat(result.output).contains("allowEmptyConsumerSources = true")
    }

    @Test
    fun `analyzeKmprofiler allows an explicit header-only audit`() {
        swiftDir.listFiles()?.forEach { it.delete() }
        buildFile.appendText(
            """

            kmprofiler {
                allowEmptyConsumerSources.set(true)
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("analyzeKmprofiler")
            .build()

        assertThat(result.task(":analyzeKmprofiler")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(File(testProjectDir, "build/reports/kmprofiler-report.md").readText())
            .contains("Swift files scanned: 0")
    }
}
