package io.github.siddhantpanhalkar.kmprofiler

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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

    /** Name prefixes to treat as external library code in the report. */
    abstract val externalPrefixes: ListProperty<String>
}
