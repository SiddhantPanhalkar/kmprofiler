package io.github.siddhantpanhalkar.kmprofiler.model

/**
 * Represents a single Objective-C declaration parsed from the generated
 * Kotlin/Native framework header (Shared.h / <FrameworkName>.h).
 */
data class ExportedDeclaration(
    /** The Kotlin fully-qualified name, preserved from the ObjC name if extractable. */
    val name: String,
    /** Declaration kind: CLASS, PROTOCOL, INTERFACE. */
    val kind: DeclarationKind,
    /** All Objective-C selector names belonging to this declaration. */
    val selectors: List<String>,
    /** Number of member declarations (methods + properties). */
    val memberCount: Int,
    /** Report bucket this declaration belongs to. */
    val category: DeclarationCategory = DeclarationCategory.APP_CODE,
)

enum class DeclarationKind { CLASS, PROTOCOL, INTERFACE }
