package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * A declaration enriched with scan evidence from Swift source analysis.
 *
 * @property declaration The original exported declaration from the ObjC header.
 * @property status The strongest match kind found across all scanned files.
 * @property confidence Confidence in the match based on match quality.
 * @property evidence Per-file evidence for all matches found. Empty when status is [MatchKind.NO_REFERENCE].
 */
data class DeclarationScanResult(
    val declaration: ExportedDeclaration,
    val status: MatchKind,
    val confidence: Confidence,
    val evidence: List<ScanEvidence> = emptyList(),
) {
    val name: String get() = declaration.name
    val kind: DeclarationKind get() = declaration.kind
    val selectors: List<String> get() = declaration.selectors
    val memberCount: Int get() = declaration.memberCount
    val category: DeclarationCategory get() = declaration.category

    val isReferenced: Boolean get() = status != MatchKind.NO_REFERENCE
}
