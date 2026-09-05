package io.github.siddhantpanhalkar.kmprofiler.parser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LinkMapComparisonTest {

    private val parser = LinkMapParser()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `compare produces correct deltas between baseline and candidate`() {
        val baseline = writeLinkMap(
            "baseline.txt",
            """
            # Object files:
            [  1] /Users/test/Shared.framework/Shared
            [  2] /Users/test/App.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example.service#fetch(){}
            0x100005000	0x00000800	[  2]	_OBJC_CLASS_${'$'}_SharedUserRepo
            """.trimIndent()
        )
        val candidate = writeLinkMap(
            "candidate.txt",
            """
            # Object files:
            [  1] /Users/test/Shared.framework/Shared
            [  2] /Users/test/App.o
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000800	[  1]	_kfun:com.example.service#fetch(){}
            0x100005000	0x00000800	[  2]	_OBJC_CLASS_${'$'}_SharedUserRepo
            0x100005800	0x00000400	[  1]	_kfun:com.example.newFunc#(){}
            """.trimIndent()
        )

        val result = parser.compare(baseline, candidate, "Shared")

        // Baseline: 4096 + 2048 = 6144 total, 4096 + 2048 = 6144 classified
        assertThat(result.baseline.totalMappedBytes).isEqualTo(6144L)
        // Candidate: 2048 + 2048 + 1024 = 5120 total
        assertThat(result.candidate.totalMappedBytes).isEqualTo(5120L)
        assertThat(result.totalDelta).isEqualTo(-1024L)

        // com.example delta: baseline 4096, candidate 2048 + 1024 = 3072, delta = -1024
        val comExampleDelta = result.categoryDeltas["com.example"]!!
        assertThat(comExampleDelta.baselineBytes).isEqualTo(4096L)
        assertThat(comExampleDelta.candidateBytes).isEqualTo(3072L)
        assertThat(comExampleDelta.deltaBytes).isEqualTo(-1024L)
    }

    @Test
    fun `compare warns when build sizes differ significantly`() {
        val baseline = writeLinkMap(
            "baseline.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example#small(){}
            """.trimIndent()
        )
        val candidate = writeLinkMap(
            "candidate.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00100000	[  1]	_kfun:com.example#huge(){}
            """.trimIndent()
        )

        val result = parser.compare(baseline, candidate, "Shared")
        assertThat(result.equivalenceWarnings).isNotEmpty
        assertThat(result.equivalenceWarnings[0]).contains("Build size changed by")
    }

    @Test
    fun `compare works when baseline has categories not in candidate`() {
        val baseline = writeLinkMap(
            "baseline.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example.foo(){}
            0x100005000	0x00000800	[  1]	_kfun:org.lib.bar(){}
            """.trimIndent()
        )
        val candidate = writeLinkMap(
            "candidate.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example.foo(){}
            """.trimIndent()
        )

        val result = parser.compare(baseline, candidate, "Shared")
        assertThat(result.categoryDeltas["org.lib"]!!.baselineBytes).isEqualTo(2048L)
        assertThat(result.categoryDeltas["org.lib"]!!.candidateBytes).isEqualTo(0L)
        assertThat(result.categoryDeltas["org.lib"]!!.deltaBytes).isEqualTo(-2048L)
    }

    @Test
    fun `compare works when candidate has categories not in baseline`() {
        val baseline = writeLinkMap(
            "baseline.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example.foo(){}
            """.trimIndent()
        )
        val candidate = writeLinkMap(
            "candidate.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example.foo(){}
            0x100005000	0x00000800	[  1]	_kfun:org.lib.bar(){}
            """.trimIndent()
        )

        val result = parser.compare(baseline, candidate, "Shared")
        assertThat(result.categoryDeltas["org.lib"]!!.baselineBytes).isEqualTo(0L)
        assertThat(result.categoryDeltas["org.lib"]!!.candidateBytes).isEqualTo(2048L)
        assertThat(result.categoryDeltas["org.lib"]!!.deltaBytes).isEqualTo(2048L)
    }

    @Test
    fun `category delta percentage is correct`() {
        val baseline = writeLinkMap(
            "baseline.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00001000	[  1]	_kfun:com.example#foo(){}
            """.trimIndent()
        )
        val candidate = writeLinkMap(
            "candidate.txt",
            """
            # Symbols:
            # Address	Size    	File  Name
            0x100004000	0x00000800	[  1]	_kfun:com.example#foo(){}
            """.trimIndent()
        )

        val result = parser.compare(baseline, candidate, "Shared")
        val delta = result.categoryDeltas["com.example"]!!
        // 2048 -> 1024, delta = -1024, percentage = -50%
        assertThat(delta.deltaPercentage).isCloseTo(-50.0, within(0.1))
    }

    private fun writeLinkMap(name: String, content: String): File {
        return tempDir.resolve(name).apply { writeText(content) }
    }
}
