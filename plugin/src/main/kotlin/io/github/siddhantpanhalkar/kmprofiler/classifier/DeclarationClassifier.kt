package io.github.siddhantpanhalkar.kmprofiler.classifier

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration

/**
 * Classifies parsed ObjC declarations into report buckets.
 *
 * Classification order (first match wins):
 *   1. User-configured external prefixes  → USER_FLAGGED_EXTERNAL
 *   2. Name ends with "Kt"                → KOTLIN_FILE_FACADE
 *   3. Underscore module-mangling pattern → LIKELY_EXTERNAL
 *   4. Everything else                    → APP_CODE
 *
 * The underscore pattern [A-Z][a-zA-Z0-9]+_[a-z] is derived directly from
 * Kotlin/Native's ObjC name mangling for cross-module types. It converts
 * Gradle module path separators (`-`) to underscores and concatenates with
 * the type name. This is a structural property of the compiler output, not
 * a hardcoded list of library names.
 */
class DeclarationClassifier(
    /**
     * Name prefixes the user has explicitly declared as external libraries.
     * The plugin does not know what libraries a project uses, so the user
     * must list prefixes the heuristic misses (e.g. "Skiko", "Material3").
     */
    private val userExternalPrefixes: Set<String> = emptySet(),
) {
    // Matches Kotlin/Native ObjC cross-module mangling: e.g. Ktor_client_coreHttpClient
    // Structure: Capital word + underscore + lowercase (module subpath continuation)
    private val externalModulePattern = Regex("^[A-Z][a-zA-Z0-9]+_[a-z]")

    fun classify(decl: ExportedDeclaration): DeclarationCategory {
        val name = decl.name

        // 1. User-declared external prefixes (checked first — user intent wins)
        if (userExternalPrefixes.any { prefix -> name.startsWith(prefix) }) {
            return DeclarationCategory.USER_FLAGGED_EXTERNAL
        }

        // 2. Kotlin file facades — reliable structural pattern from Kotlin/Native
        if (name.length > 2 && name.endsWith("Kt")) {
            return DeclarationCategory.KOTLIN_FILE_FACADE
        }

        // 3. Cross-module name mangling — underscore between module path segments
        if (externalModulePattern.containsMatchIn(name)) {
            return DeclarationCategory.LIKELY_EXTERNAL
        }

        return DeclarationCategory.APP_CODE
    }
}
