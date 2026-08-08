package io.github.siddhantpanhalkar.kmprofiler.scanner

import io.github.siddhantpanhalkar.kmprofiler.model.ExportedDeclaration
import java.io.File

/**
 * Scans Swift source files for textual references to exported declarations.
 *
 * This is a best-effort heuristic. It will produce false negatives for
 * declarations reached via:
 *   - Dependency injection containers
 *   - Protocol conformance / dynamic dispatch
 *   - KVO / selector-string lookup
 *   - Code generation
 *   - External SDK consumers outside the scanned directories
 *
 * Callers MUST surface this caveat in any user-facing output.
 */
class SwiftUsageScanner {

    /**
     * @param declarations   All declarations parsed from the ObjC header.
     * @param swiftSourceDirs Root directories to walk for `.swift` files.
     * @return Declarations for which no reference was found — review candidates only.
     */
    fun findUnreferenced(
        declarations: List<ExportedDeclaration>,
        swiftSourceDirs: Set<File>,
    ): List<ExportedDeclaration> {
        val swiftContent: String = buildSwiftCorpus(swiftSourceDirs)
        return declarations.filter { decl -> !isReferenced(decl, swiftContent) }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun buildSwiftCorpus(dirs: Set<File>): String = buildString {
        dirs.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "swift" }
                .forEach { append(it.readText()).append('\n') }
        }
    }

    /**
     * A declaration is considered "referenced" if either:
     *   (a) Its ObjC class/protocol name appears in the Swift corpus, OR
     *   (b) Any of its selectors appear (in Swift dot-notation or bracket-call form).
     *
     * This intentionally casts a wide net to minimise false positives.
     */
    private fun isReferenced(decl: ExportedDeclaration, corpus: String): Boolean {
        if (corpus.contains(decl.name)) return true
        return decl.selectors.any { selector ->
            // Convert ObjC selector to Swift method name for dot-notation check
            val swiftName = selector.substringBefore(':')
            corpus.contains(swiftName) || corpus.contains(selector)
        }
    }
}
