package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.model.Confidence
import io.github.siddhantpanhalkar.kmprofiler.model.ConfigLintResult
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import io.github.siddhantpanhalkar.kmprofiler.model.ScanResult
import io.github.siddhantpanhalkar.kmprofiler.ownership.RemediationCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownReportRendererTest {

    private fun scanResult(
        name: String,
        kind: DeclarationKind = DeclarationKind.CLASS,
        selectors: List<String> = emptyList(),
        memberCount: Int = 0,
        category: DeclarationCategory = DeclarationCategory.APP_CODE,
        status: MatchKind = MatchKind.NO_REFERENCE,
        confidence: Confidence = Confidence.HIGH,
    ) = DeclarationScanResult(
        declaration = ExportedDeclaration(name, kind, selectors, memberCount, category),
        status = status,
        confidence = confidence,
    )

    @Test
    fun `renders report with review candidates and decision tree`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                scanResult("com.acme.AuthEngine", memberCount = 14),
                scanResult("com.acme.DtoMapper", memberCount = 9),
            ),
            configLint = ConfigLintResult(
                transitiveExport = true,
                isStatic = true,
                exportedFrameworkCount = 1,
            ),
            scannedFileCount = 5,
            scannedLineCount = 420,
            headerPath = "/path/to/Shared.h",
            headerTimestamp = 1700000000000,
            remediation = mapOf(
                "com.acme.AuthEngine" to RemediationCategory.MANUAL_REVIEW_ONLY,
                "com.acme.DtoMapper" to RemediationCategory.MANUAL_REVIEW_ONLY,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("### kmprofiler: iOS Export Profile")
        assertThat(rendered).contains("**Export surface:** 3 Kotlin declarations exported to Objective-C.")
        assertThat(rendered).contains("**No direct Swift call site found for 2 of them.**")
        assertThat(rendered).doesNotContain("@file:HiddenFromObjC")
        assertThat(rendered).contains("Decision tree for each candidate")
        assertThat(rendered).contains("declaration-level `@HiddenFromObjC`")
        assertThat(rendered).contains("#### Provenance")
        assertThat(rendered).contains("**Swift files scanned:** 5")
        assertThat(rendered).contains("**Swift lines scanned:** 420")
        assertThat(rendered).contains("not proven dead code")
        assertThat(rendered).contains("Kotlin visibility changes")
        assertThat(rendered).contains("| `com.acme.AuthEngine` | class | 14 |")
        assertThat(rendered).contains("| `com.acme.DtoMapper` | class | 9 |")
        assertThat(rendered).contains("manual review only")
    }

    @Test
    fun `renders report when no candidates exist`() {
        val result = ScanResult(
            totalExported = 0,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(
                transitiveExport = null,
                isStatic = null,
                exportedFrameworkCount = 0,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("### kmprofiler: iOS Export Profile")
        assertThat(rendered).contains("**Export surface:** 0 Kotlin declarations exported to Objective-C.")
        assertThat(rendered).contains("**All exported declarations have at least one direct call site in the scanned Swift sources.**")
        assertThat(rendered).doesNotContain("@file:HiddenFromObjC")
        assertThat(rendered).contains("#### Config")
        assertThat(rendered).contains("not explicitly set (defaults to `false`)")
    }

    @Test
    fun `renders recommended and multi-framework config diagnostics`() {
        val result = ScanResult(
            totalExported = 1,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(
                transitiveExport = false,
                isStatic = false,
                exportedFrameworkCount = 2,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("`transitiveExport = false` (configured in kmprofiler)")
        assertThat(rendered).contains("`isStatic = false` (configured in kmprofiler)")
        assertThat(rendered).contains("2 frameworks declared to the profiler")
    }

    @Test
    fun `renders kind label for every declaration kind`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                scanResult("Foo", kind = DeclarationKind.CLASS, memberCount = 1),
                scanResult("BarProtocol", kind = DeclarationKind.PROTOCOL, memberCount = 2),
                scanResult("BazInterface", kind = DeclarationKind.INTERFACE, memberCount = 3),
            ),
            configLint = ConfigLintResult(
                transitiveExport = null,
                isStatic = null,
                exportedFrameworkCount = 1,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("| `Foo` | class | 1 |")
        assertThat(rendered).contains("| `BarProtocol` | protocol | 2 |")
        assertThat(rendered).contains("| `BazInterface` | interface | 3 |")
    }

    @Test
    fun `renders three sections for mixed categories`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                scanResult("CameraState", memberCount = 16),
            ),
            kotlinFileFacadeCandidates = listOf(
                scanResult(
                    "ColorKt",
                    category = DeclarationCategory.KOTLIN_FILE_FACADE,
                    memberCount = 36
                ),
            ),
            likelyExternalCandidates = listOf(
                scanResult(
                    "Koin_coreModule",
                    category = DeclarationCategory.LIKELY_EXTERNAL,
                    memberCount = 20
                ),
            ),
            configLint = ConfigLintResult(
                transitiveExport = false,
                isStatic = true,
                exportedFrameworkCount = 1,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("**No direct Swift call site found for 3 of them.**")

        assertThat(rendered).contains("#### Your code - review candidates (1)")
        assertThat(rendered).contains("| `CameraState` | class | 16 |")

        assertThat(rendered).contains("#### Kotlin file facades - review candidates (1)")
        assertThat(rendered).doesNotContain("@file:HiddenFromObjC")
        assertThat(rendered).contains("declaration-level `@HiddenFromObjC`")
        assertThat(rendered).contains("| `ColorKt` | 36 |")

        assertThat(rendered).contains("#### Unresolved ownership - 1 declarations")
        assertThat(rendered).contains("naming heuristics, not metadata")
        assertThat(rendered).contains("| `Koin_coreModule` | 20 |")
    }

    @Test
    fun `sorts candidates by member count descending and labels empty declarations`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                scanResult("Small", memberCount = 2),
                scanResult("Big", memberCount = 23),
                scanResult("MarkerProtocol", kind = DeclarationKind.PROTOCOL, memberCount = 0),
            ),
            configLint = ConfigLintResult(null, null, 1),
        )

        val rendered = MarkdownReportRenderer.render(result)
        val rows = rendered.lines().filter { it.startsWith("| `") }

        assertThat(rows).hasSize(3)
        assertThat(rows[0]).contains("Big")
        assertThat(rows[1]).contains("Small")
        assertThat(rows[2]).contains("MarkerProtocol")
        assertThat(rows[2]).contains("0 (empty)")
    }

    @Test
    fun `renders provenance with zero scan counts when not provided`() {
        val result = ScanResult(
            totalExported = 1,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(null, null, 1),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("#### Provenance")
        assertThat(rendered).contains("**Swift files scanned:** 0")
        assertThat(rendered).contains("**Swift lines scanned:** 0")
    }

    @Test
    fun `config lint labels values as configured in kmprofiler`() {
        val result = ScanResult(
            totalExported = 1,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(
                transitiveExport = true,
                isStatic = true,
                exportedFrameworkCount = 1,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("`transitiveExport = true` (configured in kmprofiler)")
        assertThat(rendered).contains("`isStatic = true` (configured in kmprofiler)")
        assertThat(rendered).contains("1 framework declared to the profiler")
    }

    @Test
    fun `renders evidence and remediation columns in declaration table`() {
        val result = ScanResult(
            totalExported = 1,
            reviewCandidates = listOf(
                DeclarationScanResult(
                    declaration = ExportedDeclaration("Foo", DeclarationKind.CLASS, emptyList(), 3),
                    status = MatchKind.NO_REFERENCE,
                    confidence = Confidence.HIGH,
                    evidence = emptyList(),
                ),
            ),
            configLint = ConfigLintResult(null, null, 1),
            remediation = mapOf("Foo" to RemediationCategory.MANUAL_REVIEW_ONLY),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("| Declaration | Kind | Members | Confidence | Remediation | Evidence |")
        assertThat(rendered).contains("| `Foo` | class | 3 | high | manual review only | none |")
    }

    @Test
    fun `renders ownership analysis heuristic note`() {
        val result = ScanResult(
            totalExported = 1,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(null, null, 1),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("**Ownership analysis:** Heuristic-based")
        assertThat(rendered).contains("MANUAL_REVIEW_ONLY")
    }
}
