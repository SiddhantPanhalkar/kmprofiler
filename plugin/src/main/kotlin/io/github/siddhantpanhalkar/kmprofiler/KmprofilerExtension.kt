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

    /**
     * Allow a header-only audit when no Swift consumer source files are found.
     * Defaults to false because a missing source path would otherwise mark every
     * exported declaration as a review candidate.
     */
    val allowEmptyConsumerSources: Property<Boolean> = objects.property(Boolean::class.java)

    /** Path to the Xcode workspace (.xcworkspace). Optional if iosProject is set. */
    val iosWorkspace: Property<File> = objects.property(File::class.java)

    /** Path to the Xcode project (.xcodeproj). Optional if iosWorkspace is set. */
    val iosProject: Property<File> = objects.property(File::class.java)

    /** The Xcode scheme to build. */
    val iosScheme: Property<String> = objects.property(String::class.java)
}

