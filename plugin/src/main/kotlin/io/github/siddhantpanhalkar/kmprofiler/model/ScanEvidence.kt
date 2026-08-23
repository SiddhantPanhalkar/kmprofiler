package io.github.siddhantpanhalkar.kmprofiler.model

/** A textual type-reference match found in a configured Swift source file. */
data class ScanEvidence(
    val filePath: String,
    val lineNumber: Int,
    val matchKind: MatchKind,
    val matchedText: String,
)

/** The strength of evidence collected by the Swift source scan. */
enum class MatchKind {
    /** A declaration name was found as a standalone token in executable Swift source. */
    TYPE_REFERENCE,

    /** No standalone declaration-name token was found in the configured Swift sources. */
    NO_REFERENCE,
}

/** A parsed exported declaration together with the evidence collected for it. */
data class DeclarationScanResult(
    val declaration: ExportedDeclaration,
    val status: MatchKind,
    val evidence: List<ScanEvidence> = emptyList(),
) {
    val name: String get() = declaration.name
    val kind: DeclarationKind get() = declaration.kind
    val memberCount: Int get() = declaration.memberCount
    val category: DeclarationCategory get() = declaration.category

    /**
     * Only a declaration-name token counts as a reference. A globally matched member
     * selector is deliberately not treated as evidence because its receiver is unknown.
     */
    val isReferenced: Boolean get() = status == MatchKind.TYPE_REFERENCE
}
