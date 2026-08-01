package io.github.siddhantpanhalkar.kmprofiler.model

/** Output of the full pipeline for one project. */
data class ScanResult(
    val totalExported: Int,
    /**
     * Declarations classified as APP_CODE with no Swift call site found.
     * These are the primary actionable findings.
     */
    val reviewCandidates: List<ExportedDeclaration>,
    /**
     * Kotlin top-level file facades (*Kt classes) with no Swift call site.
     * Can be hidden with @file:HiddenFromObjC at the file level.
     */
    val kotlinFileFacadeCandidates: List<ExportedDeclaration> = emptyList(),
    /**
     * Declarations likely from external Kotlin modules (underscore mangling
     * pattern detected), with no Swift call site. Listed separately because
     * developers cannot hide these with internal/HiddenFromObjC — they must
     * remove the export() dependency or eliminate transitiveExport.
     */
    val likelyExternalCandidates: List<ExportedDeclaration> = emptyList(),
    val configLint: ConfigLintResult,
)

data class ConfigLintResult(
    val transitiveExport: Boolean?,
    val isStatic: Boolean?,
    val exportedFrameworkCount: Int,
)
