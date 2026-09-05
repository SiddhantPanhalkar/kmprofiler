package io.github.siddhantpanhalkar.kmprofiler

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

abstract class KmprofilerExtension @Inject constructor(objects: ObjectFactory) {
    /** Generated Objective-C header to inspect. Required by the export audit. */
    abstract val headerFile: RegularFileProperty

    /** Swift directories or files scanned for direct call sites. Defaults to `iosApp/`. */
    abstract val swiftSourceDirs: ConfigurableFileCollection

    /** Framework base name declared in `binaries.framework`. */
    abstract val frameworkBaseName: Property<String>

    /**
     * Records whether the framework is static or dynamic in the audit report.
     * This helps explain how linker dead-stripping may affect the result.
     */
    val isStatic: Property<Boolean> = objects.property(Boolean::class.java)

    /** Number of framework binaries to check for possible duplicated dependencies. */
    abstract val exportedFrameworkCount: Property<Int>

    /** Name prefixes to group for ownership review. This does not ignore the declarations. */
    abstract val externalPrefixes: ListProperty<String>

    // Xcode build settings

    /** Xcode workspace (.xcworkspace), when the app uses a workspace. */
    val iosWorkspace: Property<File> = objects.property(File::class.java)

    /** Xcode project (.xcodeproj), when the app does not use a workspace. */
    val iosProject: Property<File> = objects.property(File::class.java)

    /** Xcode scheme used to generate a link map. */
    val iosScheme: Property<String> = objects.property(String::class.java)

    /** Xcode build configuration. Defaults to `Release`. */
    val xcodeConfiguration: Property<String> = objects.property(String::class.java)

    /** Xcode SDK destination. Defaults to "generic/platform=iOS". */
    val sdkDestination: Property<String> = objects.property(String::class.java)

    /** Optional target architecture override. */
    val architecture: Property<String> = objects.property(String::class.java)

    /** Optional folder for Xcode build output instead of the default DerivedData folder. */
    val derivedDataPath: Property<File> = objects.property(File::class.java)

    /** Path to an optional xcconfig file to inject custom build settings into xcodebuild. */
    val xcconfig: Property<File> = objects.property(File::class.java)

    /** Maximum wait time for xcodebuild, in minutes. Defaults to 30. */
    val xcodeTimeoutMinutes: Property<Int> = objects.property(Int::class.java)

    // Link map settings

    /** Existing link map to analyze without running xcodebuild. */
    val xcodeLinkMapFile: RegularFileProperty = objects.fileProperty()

    /** Baseline link map file for comparison against another build. */
    val baselineLinkMap: RegularFileProperty = objects.fileProperty()

    /** Candidate link map file for comparison against the baseline. */
    val candidateLinkMap: RegularFileProperty = objects.fileProperty()

    /** Kotlin/Native prefix used by exported Objective-C symbols, such as `Shared`. */
    val frameworkPrefix: Property<String> = objects.property(String::class.java)

    // Analysis settings

    /**
     * Allows the export audit to run when no Swift files are found. In that case,
     * all exported declarations are review candidates. Defaults to false.
     */
    val allowEmptyConsumerSources: Property<Boolean> = objects.property(Boolean::class.java)
}
