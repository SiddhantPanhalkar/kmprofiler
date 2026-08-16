package io.github.siddhantpanhalkar.kmprofiler.classifier

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationKind
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeclarationClassifierTest {

    private val classifier = DeclarationClassifier()

    private fun decl(name: String) =
        ExportedDeclaration(name, DeclarationKind.CLASS, emptyList(), 0)

    private fun classify(name: String) = classifier.classify(decl(name))

    @Test
    fun `app code — plain name no underscore`() {
        assertThat(classify("UserRepository")).isEqualTo(DeclarationCategory.APP_CODE)
        assertThat(classify("CameraState")).isEqualTo(DeclarationCategory.APP_CODE)
        assertThat(classify("HomeUiStateReady")).isEqualTo(DeclarationCategory.APP_CODE)
    }

    @Test
    fun `kotlin file facade — name ends with Kt`() {
        assertThat(classify("ColorKt")).isEqualTo(DeclarationCategory.KOTLIN_FILE_FACADE)
        assertThat(classify("DimensKt")).isEqualTo(DeclarationCategory.KOTLIN_FILE_FACADE)
        assertThat(classify("AppModuleKt")).isEqualTo(DeclarationCategory.KOTLIN_FILE_FACADE)
    }

    @Test
    fun `likely external — underscore module pattern`() {
        assertThat(classify("Ktor_client_coreHttpClient")).isEqualTo(DeclarationCategory.LIKELY_EXTERNAL)
        assertThat(classify("Koin_coreModule")).isEqualTo(DeclarationCategory.LIKELY_EXTERNAL)
        assertThat(classify("Kotlinx_coroutines_coreFlow")).isEqualTo(DeclarationCategory.LIKELY_EXTERNAL)
        assertThat(classify("Ui_textTextStyle")).isEqualTo(DeclarationCategory.LIKELY_EXTERNAL)
        assertThat(classify("Datastore_coreDataStore")).isEqualTo(DeclarationCategory.LIKELY_EXTERNAL)
    }

    @Test
    fun `app code — underscore in middle of word is not the module pattern`() {
        // User might name their own class with underscores (unusual but possible)
        // The pattern requires Capital_lowercase specifically
        assertThat(classify("My_Config")).isEqualTo(DeclarationCategory.APP_CODE) // Capital_Capital = app
    }

    @Test
    fun `user flagged external — matched user prefix`() {
        val classifierWithPrefixes = DeclarationClassifier(setOf("Skiko", "Material3"))
        assertThat(classifierWithPrefixes.classify(decl("SkikoCanvas"))).isEqualTo(
            DeclarationCategory.USER_FLAGGED_EXTERNAL
        )
        assertThat(classifierWithPrefixes.classify(decl("Material3Typography"))).isEqualTo(
            DeclarationCategory.USER_FLAGGED_EXTERNAL
        )
    }

    @Test
    fun `user prefix wins over kotlin facade check`() {
        val classifierWithPrefixes = DeclarationClassifier(setOf("SomeLib"))
        assertThat(classifierWithPrefixes.classify(decl("SomeLibKt"))).isEqualTo(DeclarationCategory.USER_FLAGGED_EXTERNAL)
    }

    @Test
    fun `Kt suffix on short name is not a facade`() {
        // "Kt" alone (length = 2) should not be classified as a facade
        assertThat(classify("Kt")).isEqualTo(DeclarationCategory.APP_CODE)
    }
}
