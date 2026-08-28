package io.github.siddhantpanhalkar.kmprofiler.parser

import java.io.File

/**
 * Streams through an Xcode link map file and attributes byte sizes to categories.
 *
 * Supports:
 * - Symbol parsing with coverage stats
 * - Object-file attribution (maps symbols to source object files)
 * - Baseline/candidate comparison (byte deltas between builds)
 */
class LinkMapParser {

    /**
     * Result of parsing a link map file.
     *
     * @property categories Map of category name to total byte size.
     * @property totalMappedBytes Total bytes of all symbols in the map.
     * @property classifiedBytes Total bytes attributed to categories.
     * @property unclassifiedBytes totalMappedBytes - classifiedBytes.
     * @property coveragePercentage Percentage of bytes classified.
     * @property symbolCount Total number of symbols parsed.
     * @property classifiedSymbolCount Number of symbols that matched a category.
     * @property objectFiles Map of object file index to source path.
     * @property symbols List of (symbolName, sizeBytes, objectFileIndex) tuples.
     */
    data class ParseResult(
        val categories: Map<String, Long>,
        val totalMappedBytes: Long,
        val classifiedBytes: Long,
        val symbolCount: Long,
        val classifiedSymbolCount: Long,
        val objectFiles: Map<Int, String> = emptyMap(),
        val symbols: List<SymbolEntry> = emptyList(),
    ) {
        val unclassifiedBytes: Long get() = totalMappedBytes - classifiedBytes
        val coveragePercentage: Double
            get() = if (totalMappedBytes > 0) (classifiedBytes.toDouble() / totalMappedBytes * 100) else 0.0
    }

    /**
     * A single symbol entry with its size and object file attribution.
     */
    data class SymbolEntry(
        val name: String,
        val sizeBytes: Long,
        val objectFileIndex: Int,
        val category: String?,
    )

    /**
     * Delta between a baseline and candidate parse result.
     */
    data class ComparisonResult(
        val baseline: ParseResult,
        val candidate: ParseResult,
        val categoryDeltas: Map<String, CategoryDelta>,
        val totalDelta: Long,
        val equivalenceWarnings: List<String>,
    )

    /**
     * Byte delta for a single category.
     */
    data class CategoryDelta(
        val baselineBytes: Long,
        val candidateBytes: Long,
        val deltaBytes: Long,
    ) {
        val deltaPercentage: Double
            get() = if (baselineBytes > 0) {
                (deltaBytes.toDouble() / baselineBytes * 100)
            } else {
                0.0
            }
    }

    /**
     * Attribution of a symbol to an object file and its module.
     */
    data class SymbolAttribution(
        val symbolName: String,
        val sizeBytes: Long,
        val objectFileIndex: Int,
        val objectFilePath: String,
        val moduleName: String,
        val category: String?,
    )

    companion object {
        const val IOS_EXPORT_SURFACE = "[iOS Export Surface]"

        private val KOTLIN_PREFIXES = listOf(
            "_kfun:",
            "_kclass:",
            "_ktype:",
            "_kvar:",
            "_kext:",
        )

        private val SYMBOL_LINE_REGEX =
            Regex("""^\s*(?:0x)?[0-9a-fA-F]+\s+(?:0x)?([0-9a-fA-F]+)\s+\[\s*(\d+)\s*\]\s+(.+)$""")

        private val OBJECT_FILE_LINE_REGEX =
            Regex("""^\s*\[\s*(\d+)\s*\]\s+(.+)$""")

        private val FRAMEWORK_PATH_REGEX =
            Regex("""/([^/]+)\.framework/""")

        private val OBJECT_FILE_PATH_REGEX =
            Regex("""/([^/]+)\.o$""")
    }

    /**
     * Parses the link map file and returns a [ParseResult] with categorized byte sizes.
     */
    fun parse(mapFile: File, frameworkPrefix: String): ParseResult {
        if (!mapFile.exists() || !mapFileValid(mapFile)) {
            return emptyResult()
        }

        val aggregated = mutableMapOf<String, Long>()
        var totalMappedBytes = 0L
        var classifiedBytes = 0L
        var symbolCount = 0L
        var classifiedSymbolCount = 0L
        val objectFiles = mutableMapOf<Int, String>()
        val symbols = mutableListOf<SymbolEntry>()
        var currentSection: String? = null

        mapFile.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                // Detect section transitions
                if (line.startsWith("# ")) {
                    currentSection = when {
                        line.startsWith("# Object files:") -> "objectFiles"
                        line.startsWith("# Symbols:") -> "symbols"
                        line.startsWith("# Dead Stripped Symbols:") -> "deadStripped"
                        line.startsWith("# Address") -> currentSection
                        else -> {
                            if (currentSection == "symbols") break
                            currentSection
                        }
                    }
                    continue
                }

                when (currentSection) {
                    "objectFiles" -> {
                        parseObjectFileLine(line)?.let { (index, path) ->
                            objectFiles[index] = path
                        }
                    }
                    "symbols" -> {
                        val match = SYMBOL_LINE_REGEX.find(line) ?: continue
                        val sizeHex = match.groupValues[1]
                        val fileIndex = match.groupValues[2].toIntOrNull() ?: continue
                        val symbolName = match.groupValues[3].trim()
                        val size = parseHexSize(sizeHex)
                        if (size <= 0L) continue

                        symbolCount++
                        totalMappedBytes += size

                        val category = categorize(symbolName, frameworkPrefix)
                        if (category != null) {
                            classifiedBytes += size
                            classifiedSymbolCount++
                            aggregated[category] = (aggregated[category] ?: 0L) + size
                        }

                        symbols.add(SymbolEntry(symbolName, size, fileIndex, category))
                    }
                }
            }
        }

        val sortedCategories = aggregated.entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }

        return ParseResult(
            categories = sortedCategories,
            totalMappedBytes = totalMappedBytes,
            classifiedBytes = classifiedBytes,
            symbolCount = symbolCount,
            classifiedSymbolCount = classifiedSymbolCount,
            objectFiles = objectFiles.toMap(),
            symbols = symbols,
        )
    }

    /**
     * Compares a baseline and candidate link map to produce a delta report.
     */
    fun compare(baselineFile: File, candidateFile: File, frameworkPrefix: String): ComparisonResult {
        val baseline = parse(baselineFile, frameworkPrefix)
        val candidate = parse(candidateFile, frameworkPrefix)

        val allCategories = baseline.categories.keys + candidate.categories.keys
        val categoryDeltas = allCategories.associateWith { category ->
            val baseBytes = baseline.categories[category] ?: 0L
            val candBytes = candidate.categories[category] ?: 0L
            CategoryDelta(baseBytes, candBytes, candBytes - baseBytes)
        }

        val warnings = mutableListOf<String>()
        val baseTotal = baseline.totalMappedBytes
        val candTotal = candidate.totalMappedBytes
        if (baseTotal > 0 && candTotal > 0) {
            val sizeRatio = candTotal.toDouble() / baseTotal
            if (sizeRatio > 1.5 || sizeRatio < 0.5) {
                warnings.add(
                    "Build size changed by ${String.format("%.0f%%", (sizeRatio - 1) * 100)}. " +
                            "Ensure baseline and candidate use the same architecture and configuration."
                )
            }
        }

        return ComparisonResult(
            baseline = baseline,
            candidate = candidate,
            categoryDeltas = categoryDeltas,
            totalDelta = candTotal - baseTotal,
            equivalenceWarnings = warnings,
        )
    }

    /**
     * Returns per-symbol attribution to object files.
     */
    fun attributeSymbols(parseResult: ParseResult): List<SymbolAttribution> {
        return parseResult.symbols.map { entry ->
            val filePath = parseResult.objectFiles[entry.objectFileIndex] ?: "unknown"
            val moduleName = extractModuleName(filePath)
            SymbolAttribution(
                symbolName = entry.name,
                sizeBytes = entry.sizeBytes,
                objectFileIndex = entry.objectFileIndex,
                objectFilePath = filePath,
                moduleName = moduleName,
                category = entry.category,
            )
        }.sortedByDescending { it.sizeBytes }
    }

    private fun emptyResult() = ParseResult(
        categories = emptyMap(),
        totalMappedBytes = 0,
        classifiedBytes = 0,
        symbolCount = 0,
        classifiedSymbolCount = 0,
    )

    private fun mapFileValid(file: File): Boolean {
        if (!file.isFile) return false
        if (file.length() == 0L) return false
        return try {
            file.inputStream().buffered().use { reader ->
                val header = ByteArray(4096)
                val bytesRead = reader.read(header)
                if (bytesRead <= 0) return false
                String(header, 0, bytesRead).contains("# Symbols:")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun parseObjectFileLine(line: String): Pair<Int, String>? {
        val match = OBJECT_FILE_LINE_REGEX.find(line) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        val path = match.groupValues[2].trim()
        if (path.isEmpty() || path == "linker synthesized") return null
        return index to path
    }

    private fun parseHexSize(sizeHex: String): Long {
        val clean = sizeHex.trim().removePrefix("0x").removePrefix("0X")
        return clean.toLongOrNull(16) ?: 0L
    }

    private fun categorize(symbolName: String, frameworkPrefix: String): String? {
        val isObjcExport = symbolName.startsWith("_OBJC_CLASS_\$_$frameworkPrefix") ||
                symbolName.startsWith("_OBJC_METACLASS_\$_$frameworkPrefix") ||
                symbolName.startsWith("_OBJC_IVAR_\$_$frameworkPrefix") ||
                symbolName.startsWith("_OBJC_CLASS_$$frameworkPrefix") ||
                symbolName.startsWith("_OBJC_METACLASS_$$frameworkPrefix") ||
                symbolName.startsWith("_OBJC_IVAR_$$frameworkPrefix")

        if (isObjcExport) return IOS_EXPORT_SURFACE

        if (KOTLIN_PREFIXES.any { symbolName.startsWith(it) }) {
            return extractPackage(symbolName)
        }

        return null
    }

    private fun extractPackage(symbolName: String): String? {
        val afterColon = symbolName.substringAfter(':', "")
        if (afterColon.isEmpty()) return null

        val rawPath = afterColon
            .substringBefore('#')
            .substringBefore('(')
            .substringBefore('<')
            .substringBefore(' ')
            .trim()

        val segments = rawPath.split('.').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        return if (segments.size >= 2) {
            segments.take(2).joinToString(".")
        } else {
            segments.first()
        }
    }

    private fun extractModuleName(filePath: String): String {
        FRAMEWORK_PATH_REGEX.find(filePath)?.let { return it.groupValues[1] }
        OBJECT_FILE_PATH_REGEX.find(filePath)?.let { return it.groupValues[1] }
        return File(filePath).nameWithoutExtension
    }

    private val FRAMEWORK_PATH_REGEX = Regex("""/([^/]+)\.framework/""")
    private val OBJECT_FILE_PATH_REGEX = Regex("""/([^/]+)\.o$""")
}
