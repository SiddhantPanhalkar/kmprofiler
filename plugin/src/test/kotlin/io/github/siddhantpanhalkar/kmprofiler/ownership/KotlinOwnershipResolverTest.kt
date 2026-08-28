package io.github.siddhantpanhalkar.kmprofiler.ownership

import io.github.siddhantpanhalkar.kmprofiler.model.Confidence
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import io.github.siddhantpanhalkar.kmprofiler.model.MatchKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinOwnershipResolverTest {

    private val resolver = KotlinOwnershipResolver()

    private fun scanResult(
        name: String,
        category: DeclarationCategory = DeclarationCategory.APP_CODE,
    ) = DeclarationScanResult(
        declaration = ExportedDeclaration(name, DeclarationKind.CLASS, emptyList(), 0, category),
        status = MatchKind.NO_REFERENCE,
        confidence = Confidence.HIGH,
    )

    @Test
    fun `app code declarations get MANUAL_REVIEW_ONLY`() {
        val results = resolver.resolve(listOf(scanResult("MyClass")))

        assertThat(results["MyClass"]?.category).isEqualTo(RemediationCategory.MANUAL_REVIEW_ONLY)
        assertThat(results["MyClass"]?.reason).isNotEmpty()
    }

    @Test
    fun `likely external declarations get DEPENDENCY_CONFIGURATION_REVIEW`() {
        val results = resolver.resolve(
            listOf(scanResult("Ktor_client_core", DeclarationCategory.LIKELY_EXTERNAL))
        )

        assertThat(results["Ktor_client_core"]?.category)
            .isEqualTo(RemediationCategory.DEPENDENCY_CONFIGURATION_REVIEW)
        assertThat(results["Ktor_client_core"]?.reason).contains("external library")
    }

    @Test
    fun `user flagged external declarations get DEPENDENCY_CONFIGURATION_REVIEW`() {
        val results = resolver.resolve(
            listOf(scanResult("SkikoCanvas", DeclarationCategory.USER_FLAGGED_EXTERNAL))
        )

        assertThat(results["SkikoCanvas"]?.category)
            .isEqualTo(RemediationCategory.DEPENDENCY_CONFIGURATION_REVIEW)
    }

    @Test
    fun `kotlin file facade declarations get MANUAL_REVIEW_ONLY`() {
        val results = resolver.resolve(
            listOf(scanResult("ColorKt", DeclarationCategory.KOTLIN_FILE_FACADE))
        )

        assertThat(results["ColorKt"]?.category).isEqualTo(RemediationCategory.MANUAL_REVIEW_ONLY)
    }

    @Test
    fun `empty input returns empty map`() {
        val results = resolver.resolve(emptyList())

        assertThat(results).isEmpty()
    }

    @Test
    fun `multiple declarations resolved independently`() {
        val results = resolver.resolve(
            listOf(
                scanResult("MyClass"),
                scanResult("Ktor_client", DeclarationCategory.LIKELY_EXTERNAL),
                scanResult("Skiko", DeclarationCategory.USER_FLAGGED_EXTERNAL),
            )
        )

        assertThat(results["MyClass"]?.category).isEqualTo(RemediationCategory.MANUAL_REVIEW_ONLY)
        assertThat(results["Ktor_client"]?.category).isEqualTo(RemediationCategory.DEPENDENCY_CONFIGURATION_REVIEW)
        assertThat(results["Skiko"]?.category).isEqualTo(RemediationCategory.DEPENDENCY_CONFIGURATION_REVIEW)
    }
}
