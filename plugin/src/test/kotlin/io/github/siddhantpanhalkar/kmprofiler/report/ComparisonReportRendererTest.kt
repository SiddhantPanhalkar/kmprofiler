package io.github.siddhantpanhalkar.kmprofiler.report

import io.github.siddhantpanhalkar.kmprofiler.parser.LinkMapParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ComparisonReportRendererTest {

    @Test
    fun `renders negative delta with minus sign`() {
        val renderer = ComparisonReportRenderer()
        val emptyResult = LinkMapParser.ParseResult(emptyMap(), 0, 0, 0, 0)

        val comparison = LinkMapParser.ComparisonResult(
            baseline = emptyResult.copy(totalMappedBytes = 2048),
            candidate = emptyResult.copy(totalMappedBytes = 1024),
            categoryDeltas = mapOf("Test" to LinkMapParser.CategoryDelta(2048, 1024, -1024)),
            totalDelta = -1024,
            equivalenceWarnings = emptyList()
        )

        val rendered = renderer.render(comparison)
        assertThat(rendered).contains("| -1.00 KB |")
    }
}
