package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.model.ConfigLintResult
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import io.github.siddhantpanhalkar.kmprofiler.model.ScanResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownReportRendererTest {

    private fun candidate(
        name: String,
        category: DeclarationCategory = DeclarationCategory.APP_CODE,
        members: Int = 1,
    ) = DeclarationScanResult(
        declaration = ExportedDeclaration(
            name,
            DeclarationKind.CLASS,
            emptyList(),
            members,
            category
        ),
        status = MatchKind.NO_REFERENCE,
    )

    @Test
    fun `renders conservative audit claims and provenance`() {
        val report = MarkdownReportRenderer.render(
            ScanResult(
                totalExported = 3,
                reviewCandidates = listOf(candidate("AuthEngine", members = 14)),
                configLint = ConfigLintResult(true, true, 1),
                scannedFileCount = 2,
                scannedLineCount = 19,
                headerPath = "/tmp/Shared.h",
                headerTimestamp = 1,
            ),
        )

        assertThat(report).contains("Review candidates are not unused-code findings")
        assertThat(report).contains("does not treat a member name such as `start()`")
        assertThat(report).contains("Header: `/tmp/Shared.h`")
        assertThat(report).contains("Swift files scanned: 2")
        assertThat(report).contains("| `AuthEngine` | class | 14 | no type token found |")
        assertThat(report).contains("does not analyze Kotlin call graphs")
    }

    @Test
    fun `uses valid declaration-level HiddenFromObjC guidance for file facades`() {
        val report = MarkdownReportRenderer.render(
            ScanResult(
                totalExported = 1,
                reviewCandidates = emptyList(),
                kotlinFileFacadeCandidates = listOf(
                    candidate(
                        "ColorsKt",
                        DeclarationCategory.KOTLIN_FILE_FACADE
                    )
                ),
                configLint = ConfigLintResult(null, null, 1),
            ),
        )

        assertThat(report).contains("`@HiddenFromObjC` is valid on eligible")
        assertThat(report).contains("It is not a file annotation.")
        assertThat(report).doesNotContain("@file:HiddenFromObjC")
    }

    @Test
    fun `labels ownership classification as a hint`() {
        val report = MarkdownReportRenderer.render(
            ScanResult(
                totalExported = 1,
                reviewCandidates = emptyList(),
                likelyExternalCandidates = listOf(
                    candidate(
                        "Koin_coreModule",
                        DeclarationCategory.LIKELY_EXTERNAL
                    )
                ),
                configLint = ConfigLintResult(false, false, 2),
            ),
        )

        assertThat(report).contains("classification hint, not evidence")
        assertThat(report).contains("Exported framework count supplied to kmprofiler: 2")
        assertThat(report).doesNotContain("originate from transitive dependencies")
    }
}
