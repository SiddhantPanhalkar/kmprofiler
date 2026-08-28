package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * Classification of a parsed ObjC declaration for report bucketing.
 *
 * The classification is intentionally conservative:
 *   - [LIKELY_EXTERNAL] is a heuristic, not a guarantee.
 *   - [KOTLIN_FILE_FACADE] means the exported name ends in the conventional `Kt` suffix.
 *   - [APP_CODE] is the default when no other naming pattern matches.
 *   - [USER_FLAGGED_EXTERNAL] means the user explicitly told the plugin this prefix
 *     is from an external library via [io.github.siddhantpanhalkar.kmprofiler.KmprofilerExtension.externalPrefixes].
 */
enum class DeclarationCategory {
    /** No external-module naming pattern, configured prefix, or file-facade suffix matched. */
    APP_CODE,

    /**
     * Exported name with the conventional Kotlin file-facade `Kt` suffix.
     * Example: `ColorKt` may wrap top-level declarations from `Color.kt`.
     * Review the eligible declarations behind a facade individually. `HiddenFromObjC`
     * is not valid as a file annotation.
     */
    KOTLIN_FILE_FACADE,

    /**
     * Likely from an external Kotlin module, detected by the Kotlin/Native ObjC
     * name mangling pattern: `ModuleName_submoduleTypeName`.
     *
     * The mangling uses underscores between Gradle module path segments, e.g.:
     *   `ktor-client-core` → `Ktor_client_core` prefix
     *   `koin-core`        → `Koin_core` prefix
     *   `kotlinx-coroutines-core` → `Kotlinx_coroutines_core` prefix
     *
     * This pattern is only a classification hint. App code can have the same shape, and
     * some external libraries use simple prefixes. Use [USER_FLAGGED_EXTERNAL] for
     * known prefixes via the `externalPrefixes` extension property.
     */
    LIKELY_EXTERNAL,

    /**
     * Matched a prefix the user explicitly listed in `externalPrefixes`.
     * Use this for libraries the underscore heuristic misses (e.g. `Skiko`, `Material3`).
     */
    USER_FLAGGED_EXTERNAL,
}
