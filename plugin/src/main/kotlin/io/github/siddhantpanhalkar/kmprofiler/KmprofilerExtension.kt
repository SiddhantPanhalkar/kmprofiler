package io.github.siddhantpanhalkar.kmprofiler

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

abstract class KmprofilerExtension @Inject constructor(objects: ObjectFactory) {
    /** Path to the generated ObjC header (Shared.h). Must be set by consumer. */
    abstract val headerFile: RegularFileProperty

    /** Directories containing Swift source files. Defaults to `iosApp/` at project root. */
    abstract val swiftSourceDirs: ConfigurableFileCollection

    /** The framework baseName used in `binaries.framework { baseName = "..." }`. Defaults to "Shared". */
    abstract val frameworkBaseName: Property<String>

    /**
     * Whether the framework is built as a static archive (`isStatic = true`).
     * Null (default) = not explicitly configured; reported as unknown.
     */
    val isStatic: Property<Boolean> = objects.property(Boolean::class.java)

    /** Number of distinct exported framework binaries in this project. Defaults to 1. */
    abstract val exportedFrameworkCount: Property<Int>

    /** Name prefixes to group for ownership review. This does not establish module ownership. */
    abstract val externalPrefixes: ListProperty<String>

    // ── Xcode build settings ─────────────────────────────────────────────

    /** Path to the Xcode workspace (.xcworkspace). Optional if iosProject is set. */
    val iosWorkspace: Property<File> = objects.property(File::class.java)

    /** Path to the Xcode project (.xcodeproj). Optional if iosWorkspace is set. */
    val iosProject: Property<File> = objects.property(File::class.java)

    /** The Xcode scheme to build. */
    val iosScheme: Property<String> = objects.property(String::class.java)

    /** Xcode build configuration. Defaults to "Release". */
    val xcodeConfiguration: Property<String> = objects.property(String::class.java)

    /** Xcode SDK destination. Defaults to "generic/platform=iOS". */
    val sdkDestination: Property<String> = objects.property(String::class.java)

    /** Target architecture (e.g. "arm64"). Optional — auto-detected if not set. */
    val architecture: Property<String> = objects.property(String::class.java)

    /** Custom derived data path. Optional — Xcode default used if not set. */
    val derivedDataPath: Property<File> = objects.property(File::class.java)

    /** Custom xcconfig file. Optional. */
    val xcconfig: Property<File> = objects.property(File::class.java)

    /** Timeout for xcodebuild operations in minutes. Defaults to 30. */
    val xcodeTimeoutMinutes: Property<Int> = objects.property(Int::class.java)

    // ── Link map settings ────────────────────────────────────────────────

    /** Path to an existing Xcode link map file. */
    val xcodeLinkMapFile: RegularFileProperty = objects.fileProperty()

    /** Framework prefix for ObjC symbols in the link map. Defaults to frameworkBaseName or "Shared". */
    val frameworkPrefix: Property<String> = objects.property(String::class.java)

    // ── Analysis settings ────────────────────────────────────────────────

    /**
     * When false (default), the analyzeKmprofiler task fails if zero Swift source files
     * are discovered in the configured directories. Set to true to allow analysis with
     * no consumer sources (all declarations will be reported as candidates).
     */
    val allowEmptyConsumerSources: Property<Boolean> = objects.property(Boolean::class.java)
}
