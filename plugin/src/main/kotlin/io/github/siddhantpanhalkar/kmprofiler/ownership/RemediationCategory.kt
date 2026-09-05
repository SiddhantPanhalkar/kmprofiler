package io.github.siddhantpanhalkar.kmprofiler.ownership

/**
 * The recommended remediation action for a declaration, determined by the
 * ownership resolver. Each category has specific preconditions that must
 * be proven before the recommendation is emitted.
 *
 * Without KLIB/compiler metadata, the resolver degrades to
 * [MANUAL_REVIEW_ONLY] for all declarations. The other categories require
 * metadata to prove their preconditions.
 */
enum class RemediationCategory {
    /**
     * Declaration-level `@HiddenFromObjC` is a candidate.
     * Preconditions: declaration is public, not inherited by any public type,
     * not returned/accepted by any public function, and eligible for the annotation.
     */
    HIDDEN_FROM_OBJC_CANDIDATE,

    /**
     * Changing to `internal` may be safe.
     * Preconditions: declaration is public, used only within the same compilation
     * module, not exposed by any public API, and not part of expect/actual.
     */
    SAFE_INTERNAL_CANDIDATE,

    /**
     * Changing to `private` may be safe.
     * Preconditions: declaration is public, used only within the same file/class,
     * not exposed by any public API.
     */
    SAFE_PRIVATE_CANDIDATE,

    /**
     * A coordinated API-chain change is required.
     * Preconditions: declaration is public, and at least one other public/protected
     * declaration exposes, inherits, accepts, or returns it. Changing only this
     * declaration would break the public API.
     */
    COORDINATED_API_CHAIN_CHANGE,

    /**
     * The declaration appears to originate from a dependency. Review the
     * dependency/export configuration rather than editing source.
     */
    DEPENDENCY_CONFIGURATION_REVIEW,

    /**
     * Insufficient information to make a safe recommendation.
     * The developer must manually inspect the declaration, its usages,
     * and the public API graph.
     */
    MANUAL_REVIEW_ONLY,
}
