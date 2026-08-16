package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * Classification of a parsed ObjC declaration for report bucketing.
 *
 * The classification is intentionally conservative:
 *   - [LIKELY_EXTERNAL] is a heuristic, not a guarantee.
 *   - [KOTLIN_FILE_FACADE] is reliable (Kotlin/Native always emits `*Kt` suffix).
 *   - [APP_CODE] is the default — anything that doesn't match the other patterns.
 *   - [USER_FLAGGED_EXTERNAL] means the user explicitly told the plugin this prefix
 *     is from an external library via [io.github.siddhantpanhalkar.kmprofiler.KmprofilerExtension.externalPrefixes].
 */
enum class DeclarationCategory {
    /** Your Kotlin code. No underscore module pattern, not a file facade. */
    APP_CODE,

    /**
     * Top-level Kotlin file facade. Kotlin/Native emits a `*Kt` ObjC class for
     * every Kotlin source file that contains top-level functions.
     * Example: `ColorKt` wraps all top-level functions in `Color.kt`.
     * These can be hidden with `@file:HiddenFromObjC` at the file level.
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
     * This pattern is reliable but not exhaustive — some libraries (e.g. Skia/Skiko,
     * some SDK models) use simple prefixes without underscores. Use [USER_FLAGGED_EXTERNAL]
     * for those via the `externalPrefixes` extension property.
     */
    LIKELY_EXTERNAL,

    /**
     * Matched a prefix the user explicitly listed in `externalPrefixes`.
     * Use this for libraries the underscore heuristic misses (e.g. `Skiko`, `Material3`).
     */
    USER_FLAGGED_EXTERNAL,
}
