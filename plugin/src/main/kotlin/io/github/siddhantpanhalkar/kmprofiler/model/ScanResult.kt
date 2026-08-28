package io.github.siddhantpanhalkar.kmprofiler.model

import io.github.siddhantpanhalkar.kmprofiler.ownership.RemediationCategory

/** Output of the full pipeline for one project. */
data class ScanResult(
    val totalExported: Int,
    /**
     * Declarations classified as APP_CODE with no direct Swift call site detected.
     * These are review candidates — not proof of dead code or removable adapters.
     */
    val reviewCandidates: List<DeclarationScanResult>,
    /**
     * Kotlin top-level file facades (*Kt classes) with no direct Swift call site detected.
     * Review individual eligible declarations for declaration-level @HiddenFromObjC
     * or visibility changes. Do not apply file-level annotations.
     */
    val kotlinFileFacadeCandidates: List<DeclarationScanResult> = emptyList(),
    /**
     * Declarations whose names match cross-module mangling patterns or user-configured
     * external prefixes, with no direct Swift call site detected. Ownership is inferred
     * from naming heuristics, not metadata. Report the dependency/export path for review.
     */
    val likelyExternalCandidates: List<DeclarationScanResult> = emptyList(),
    val configLint: ConfigLintResult,
    /** Number of Swift source files actually scanned. */
    val scannedFileCount: Int = 0,
    /** Total lines across all scanned Swift source files. */
    val scannedLineCount: Int = 0,
    /** Header file path used for this scan, for provenance. */
    val headerPath: String = "",
    /** Timestamp of when the header file was last modified. */
    val headerTimestamp: Long = 0,
    /** Per-declaration remediation categories from ownership analysis. */
    val remediation: Map<String, RemediationCategory> = emptyMap(),
)

data class ConfigLintResult(
    val transitiveExport: Boolean?,
    val isStatic: Boolean?,
    val exportedFrameworkCount: Int,
)
