package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * Evidence for a single match of a declaration in a Swift source file.
 *
 * @property filePath Path to the Swift source file containing the match.
 * @property lineNumber 1-based line number where the match was found.
 * @property matchKind Type of match detected.
 * @property matchedText The exact text that matched in the source.
 */
data class ScanEvidence(
    val filePath: String,
    val lineNumber: Int,
    val matchKind: MatchKind,
    val matchedText: String,
)

/**
 * The kind of match found in Swift source code.
 */
enum class MatchKind {
    /** Declaration name found as a standalone type reference (e.g. `let x = Foo()`). */
    TYPE_REFERENCE,

    /** One of the declaration's member selectors found as a standalone reference. */
    MEMBER_REFERENCE,

    /** Declaration name found in a comment or string literal, which is weak evidence. */
    WEAK_TEXTUAL,

    /** No reference found in any scanned file. */
    NO_REFERENCE,
}

/**
 * Confidence level for a match.
 *
 * HIGH: Type name found as standalone token in executable code.
 * MEDIUM: Member selector found but type name not found as standalone token.
 * LOW: Name found only in comments or string literals.
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}
