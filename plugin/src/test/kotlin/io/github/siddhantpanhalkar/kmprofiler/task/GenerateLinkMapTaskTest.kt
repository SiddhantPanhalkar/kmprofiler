package io.github.siddhantpanhalkar.kmprofiler.task

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GenerateLinkMapTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resolveArchitecture resolves arm64 when undefined_arch is set in ARCHS`() {
        val output = """
            ARCHS = undefined_arch arm64
        """.trimIndent()
        val arch = GenerateLinkMapTask.resolveArchitecture(null, output)
        assertThat(arch).isEqualTo("arm64")
    }

    @Test
    fun `resolveArchitecture resolves arm64 when CURRENT_ARCH is undefined and NATIVE_ARCH_ACTUAL is arm64`() {
        val output = """
            CURRENT_ARCH = undefined_arch
            NATIVE_ARCH_ACTUAL = arm64
        """.trimIndent()
        val arch = GenerateLinkMapTask.resolveArchitecture(null, output)
        assertThat(arch).isEqualTo("arm64")
    }

    @Test
    fun `resolveLinkMapFile falls back when LD_MAP_FILE_PATH contains undefined_arch`() {
        val tempSubDir = tempDir.resolve("Temp").apply { mkdirs() }
        val linkMap = tempSubDir.resolve("App-LinkMap-1-arm64.txt").apply { writeText("content") }

        val output = """
            LD_MAP_FILE_PATH = /path/to/undefined_arch/map.txt
            TARGET_TEMP_DIR = ${tempSubDir.absolutePath}
        """.trimIndent()

        val file = GenerateLinkMapTask.resolveLinkMapFile(output, "arm64")
        assertThat(file).isEqualTo(linkMap)
    }
}
