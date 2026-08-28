package io.github.siddhantpanhalkar.kmprofiler.model

/** Output of the v1 export audit for one framework header and Swift source set. */
data class ScanResult(
    val totalExported: Int,
    /** App-code declarations with no declaration-name token found in the scan. */
    val reviewCandidates: List<DeclarationScanResult>,
    /** Kotlin top-level file facades with no declaration-name token found in the scan. */
    val kotlinFileFacadeCandidates: List<DeclarationScanResult> = emptyList(),
    /** Declarations classified by a name heuristic or configured prefix, for review. */
    val likelyExternalCandidates: List<DeclarationScanResult> = emptyList(),
    val configLint: ConfigLintResult,
    val scannedFileCount: Int = 0,
    val scannedLineCount: Int = 0,
    val headerPath: String = "",
    val headerTimestamp: Long = 0,
)

data class ConfigLintResult(
    val transitiveExport: Boolean?,
    val isStatic: Boolean?,
    val exportedFrameworkCount: Int,
)
