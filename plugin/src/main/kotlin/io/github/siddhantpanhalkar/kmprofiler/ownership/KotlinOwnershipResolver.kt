package io.github.siddhantpanhalkar.kmprofiler.ownership

import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationScanResult

/**
 * Resolves ownership and emits a remediation category for each declaration.
 *
 * Without KLIB/compiler metadata, this resolver uses heuristics derived from
 * the ObjC header analysis and scan results. It degrades to
 * [RemediationCategory.MANUAL_REVIEW_ONLY] when preconditions cannot be proven.
 *
 * When KLIB metadata becomes available (version-gated), this resolver can be
 * extended to compute the full public-API graph and prove remediation preconditions.
 */
class KotlinOwnershipResolver {

    /**
     * Result of ownership resolution for a single declaration.
     *
     * @property category The recommended remediation action.
     * @property reason Human-readable explanation of why this category was chosen.
     * @property blockingApiPaths Public API paths that block the recommended change, if any.
     */
    data class OwnershipResult(
        val category: RemediationCategory,
        val reason: String,
        val blockingApiPaths: List<String> = emptyList(),
    )

    /**
     * Resolve ownership for a list of scan results.
     *
     * @param scanResults Evidence-bearing scan results from the Swift scanner.
     * @return Map from declaration name to [OwnershipResult].
     */
    fun resolve(scanResults: List<DeclarationScanResult>): Map<String, OwnershipResult> {
        return scanResults.associate { scanResult ->
            scanResult.name to resolveOne(scanResult)
        }
    }

    private fun resolveOne(scanResult: DeclarationScanResult): OwnershipResult {
        val category = scanResult.declaration.category

        // External declarations: dependency configuration review
        if (category == DeclarationCategory.LIKELY_EXTERNAL ||
            category == DeclarationCategory.USER_FLAGGED_EXTERNAL
        ) {
            return OwnershipResult(
                category = RemediationCategory.DEPENDENCY_CONFIGURATION_REVIEW,
                reason = "Declaration matches an external library naming pattern. " +
                        "Review the dependency/export configuration rather than editing source.",
            )
        }

        // Without KLIB metadata, we cannot prove the preconditions for any
        // specific remediation. All app-code declarations require manual review.
        return OwnershipResult(
            category = RemediationCategory.MANUAL_REVIEW_ONLY,
            reason = "Ownership and API-graph analysis requires Kotlin compiler metadata " +
                    "(KLIB) which is not yet available. Manually inspect whether this " +
                    "declaration is intentionally public, whether it is exposed by other " +
                    "public APIs, and whether a visibility change or @HiddenFromObjC " +
                    "annotation is safe.",
        )
    }
}
