package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * Classification of a parsed ObjC declaration for report bucketing.
 *
 * All classifications are heuristic. Name-based inference cannot prove ownership;
 * only KLIB/module metadata can establish that.
 *
 *   - [APP_CODE]: default bucket; does not match any other pattern.
 *   - [KOTLIN_FILE_FACADE]: name ends with `Kt` (Kotlin/Native emits these for
 *     source files containing top-level functions). Ownership still comes from a name heuristic.
 *   - [LIKELY_EXTERNAL]: name matches the Kotlin/Native ObjC name mangling pattern
 *     (`ModuleName_submoduleTypeName`). Indicates cross-module origin but does not
 *     prove the declaration belongs to a third-party dependency.
 *   - [USER_FLAGGED_EXTERNAL]: user explicitly listed this prefix via
 *     [io.github.siddhantpanhalkar.kmprofiler.KmprofilerExtension.externalPrefixes].
 *     Affect grouping only; does not change safety recommendations.
 */
enum class DeclarationCategory {
    /**
     * Default bucket. No underscore module pattern detected, not a file facade.
     * Ownership is unresolved without metadata.
     */
    APP_CODE,

    /**
     * Top-level Kotlin file facade. Kotlin/Native emits a `*Kt` ObjC class for
     * every Kotlin source file that contains top-level functions.
     * Example: `ColorKt` wraps all top-level functions in `Color.kt`.
     *
     * If iOS never uses these functions, the developer should review eligible
     * individual declarations for declaration-level `@HiddenFromObjC` or
     * visibility changes. Do not apply file-level annotations.
     */
    KOTLIN_FILE_FACADE,

    /**
     * Name resembles the Kotlin/Native ObjC name mangling pattern
     * (`ModuleName_submoduleTypeName`), e.g.:
     *   `ktor-client-core` → `Ktor_client_core` prefix
     *   `koin-core`        → `Koin_core` prefix
     *   `kotlinx-coroutines-core` → `Kotlinx_coroutines_core` prefix
     *
     * This is a naming heuristic, not proof of external ownership. Some app-owned
     * wrappers or transitive types may match. Use [USER_FLAGGED_EXTERNAL] for
     * libraries the underscore heuristic misses via the `externalPrefixes` property.
     */
    LIKELY_EXTERNAL,

    /**
     * Matched a prefix the user explicitly listed in `externalPrefixes`.
     * Affects grouping in the report; does not change the safety recommendation.
     * The developer must still review whether the declaration is truly external.
     */
    USER_FLAGGED_EXTERNAL,
}
