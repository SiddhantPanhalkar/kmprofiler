package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.model.ConfigLintResult
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.ScanResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MarkdownReportRendererTest {

    @Test
    fun `renders exact v0_1 report snapshot with review candidates`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                ExportedDeclaration("com.acme.AuthEngine", DeclarationKind.CLASS, emptyList(), 14),
                ExportedDeclaration("com.acme.DtoMapper", DeclarationKind.CLASS, emptyList(), 9),
            ),
            configLint = ConfigLintResult(
                transitiveExport = true,
                isStatic = true,
                exportedFrameworkCount = 1,
            ),
        )

        val expected = """
            ### 📊 KMP iOS Export Profile — v0.1

            **Export surface:** 3 Kotlin declarations exported to Objective-C.
            **No direct Swift call site found for 2 of them.**

            > ⚠️ App-size impact not yet measured. Enable `Write Link Map File` in Xcode
            > and re-run with `--link-map` for linked byte attribution. Note that raw
            > `.xcframework` size is *not* your app-size delta — it includes simulator
            > slices and all architectures.

            #### Your code — review candidates (2)

            No direct call site found in scanned Swift sources.

            | Declaration | Kind | Members |
            |---|---|---|
            | `com.acme.AuthEngine` | class | 14 |
            | `com.acme.DtoMapper` | class | 9 |

            Hiding these with `internal` or `@HiddenFromObjC` removes their ObjC adapters.
            **Verify first** — declarations used via DI, protocol conformance, or external
            SDK consumers will not appear as call sites here.

            > 💡 Seeing names you didn't write (e.g. SDK types like `SkikoCanvas` or `ModelsCustomerInfo`)?
            > Those are library internals the auto-classifier couldn't identify automatically.
            > Add their name prefix to `externalPrefixes` in your `kmprofiler {}` block
            > and they will move to the 'Library internals' section on next run:
            > ```kotlin
            > kmprofiler {
            >     externalPrefixes.addAll("Skiko", "Models")
            > }
            > ```

            #### Config
            - ❌ `transitiveExport = true` — not recommended; pulls all transitive dependencies of exported deps into the framework.
            - ℹ️ `isStatic = true` — app-linker dead-stripping applies; raw framework size will substantially overstate app delta.
            - ✅ Single exported framework; no cross-framework type duplication detected.
        """.trimIndent() + "\n"

        assertThat(MarkdownReportRenderer.render(result)).isEqualTo(expected)
    }

    @Test
    fun `renders exact v0_1 report snapshot when no candidates exist`() {
        val result = ScanResult(
            totalExported = 0,
            reviewCandidates = emptyList(),
            configLint = ConfigLintResult(
                transitiveExport = null,
                isStatic = null,
                exportedFrameworkCount = 0,
            ),
        )

        val expected = """
            ### 📊 KMP iOS Export Profile — v0.1

            **Export surface:** 0 Kotlin declarations exported to Objective-C.
            **All exported declarations have at least one direct call site in the scanned Swift sources.** ✅

            > ⚠️ App-size impact not yet measured. Enable `Write Link Map File` in Xcode
            > and re-run with `--link-map` for linked byte attribution. Note that raw
            > `.xcframework` size is *not* your app-size delta — it includes simulator
            > slices and all architectures.

            #### Config
            - ℹ️ `transitiveExport` — not explicitly set (defaults to `false`).
            - ℹ️ `isStatic` — not explicitly set; check Xcode `Embed Frameworks` settings.
            - ℹ️ No exported frameworks detected (check your `binaries.framework {}` block).
        """.trimIndent() + "\n"

        assertThat(MarkdownReportRenderer.render(result)).isEqualTo(expected)
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

        val expected = """
            ### 📊 KMP iOS Export Profile — v0.1

            **Export surface:** 1 Kotlin declarations exported to Objective-C.
            **All exported declarations have at least one direct call site in the scanned Swift sources.** ✅

            > ⚠️ App-size impact not yet measured. Enable `Write Link Map File` in Xcode
            > and re-run with `--link-map` for linked byte attribution. Note that raw
            > `.xcframework` size is *not* your app-size delta — it includes simulator
            > slices and all architectures.

            #### Config
            - ✅ `transitiveExport = false` — correct default.
            - ℹ️ `isStatic = false` — dynamic linkage; whole thinned framework is embedded. Valid for modules with C/ObjC++ dependencies.
            - ⚠️ 2 exported frameworks detected. Each is compiled independently — shared external types (e.g. coroutines) will be duplicated across binaries.
        """.trimIndent() + "\n"

        assertThat(MarkdownReportRenderer.render(result)).isEqualTo(expected)
    }

    @Test
    fun `renders kind label for every declaration kind`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                ExportedDeclaration("Foo", DeclarationKind.CLASS, emptyList(), 1),
                ExportedDeclaration("BarProtocol", DeclarationKind.PROTOCOL, emptyList(), 2),
                ExportedDeclaration("BazInterface", DeclarationKind.INTERFACE, emptyList(), 3),
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
                ExportedDeclaration("CameraState", DeclarationKind.CLASS, emptyList(), 16),
            ),
            kotlinFileFacadeCandidates = listOf(
                ExportedDeclaration("ColorKt", DeclarationKind.CLASS, emptyList(), 36),
            ),
            likelyExternalCandidates = listOf(
                ExportedDeclaration("Koin_coreModule", DeclarationKind.CLASS, emptyList(), 20),
            ),
            configLint = ConfigLintResult(
                transitiveExport = false,
                isStatic = true,
                exportedFrameworkCount = 1,
            ),
        )

        val rendered = MarkdownReportRenderer.render(result)

        assertThat(rendered).contains("**No direct Swift call site found for 3 of them.**")

        assertThat(rendered).contains("#### Your code — review candidates (1)")
        assertThat(rendered).contains("| `CameraState` | class | 16 |")

        assertThat(rendered).contains("#### Kotlin file facades — review candidates (1)")
        assertThat(rendered).contains("@file:HiddenFromObjC")
        assertThat(rendered).contains("| `ColorKt` | 36 |")

        assertThat(rendered).contains("#### Likely library internals — 1 declarations")
        assertThat(rendered).contains("**You cannot hide these with `internal` or `@HiddenFromObjC`.**")
        assertThat(rendered).contains("| `Koin_coreModule` | 20 |")
    }

    @Test
    fun `sorts candidates by member count descending and labels empty declarations`() {
        val result = ScanResult(
            totalExported = 3,
            reviewCandidates = listOf(
                ExportedDeclaration("Small", DeclarationKind.CLASS, emptyList(), 2),
                ExportedDeclaration("Big", DeclarationKind.CLASS, emptyList(), 23),
                ExportedDeclaration("MarkerProtocol", DeclarationKind.PROTOCOL, emptyList(), 0),
            ),
            configLint = ConfigLintResult(null, null, 1),
        )

        val rendered = MarkdownReportRenderer.render(result)
        val rows = rendered.lines().filter { it.startsWith("| `") }

        assertThat(rows).containsExactly(
            "| `Big` | class | 23 |",
            "| `Small` | class | 2 |",
            "| `MarkerProtocol` | protocol | 0 (empty) |",
        )
    }
}