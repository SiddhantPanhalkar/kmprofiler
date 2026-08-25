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
 * The underscore pattern is a naming heuristic. It can help group declarations
 * for ownership review, but it does not prove that a declaration comes from a
 * dependency or from a particular Kotlin module.
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

        // 1. User-declared ownership-review prefixes. User configuration wins.
        if (userExternalPrefixes.any { prefix -> name.startsWith(prefix) }) {
            return DeclarationCategory.USER_FLAGGED_EXTERNAL
        }

        // 2. Conventional Kotlin file-facade suffix.
        if (name.length > 2 && name.endsWith("Kt")) {
            return DeclarationCategory.KOTLIN_FILE_FACADE
        }

        // 3. Possible cross-module name mangling with underscores between segments.
        if (externalModulePattern.containsMatchIn(name)) {
            return DeclarationCategory.LIKELY_EXTERNAL
        }

        return DeclarationCategory.APP_CODE
    }
}
