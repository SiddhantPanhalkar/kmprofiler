package io.github.siddhantpanhalkar.kmprofiler.task

import io.github.siddhantpanhalkar.kmprofiler.classifier.DeclarationClassifier
import io.github.siddhantpanhalkar.kmprofiler.model.ConfigLintResult
import io.github.siddhantpanhalkar.kmprofiler.model.DeclarationCategory
import io.github.siddhantpanhalkar.kmprofiler.model.ScanResult
import io.github.siddhantpanhalkar.kmprofiler.parser.ObjCHeaderParser
import io.github.siddhantpanhalkar.kmprofiler.report.MarkdownReportRenderer
import io.github.siddhantpanhalkar.kmprofiler.scanner.SwiftUsageScanner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class AnalyzeKmprofilerTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val headerFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val swiftSources: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val transitiveExport: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val staticLinkage: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val exportedFrameworkCount: Property<Int>

    @get:Input
    abstract val externalPrefixes: ListProperty<String>

    @get:Input
    abstract val allowEmptyConsumerSources: Property<Boolean>

    @get:OutputFile
    abstract val reportOutput: RegularFileProperty

    @TaskAction
    fun analyze() {
        val resolvedHeaderFile = headerFile.get().asFile
        val header = resolvedHeaderFile.readText()
        val swiftDir = swiftSources.files

        if (header.isBlank()) {
            throw GradleException(
                "Header file is empty: ${resolvedHeaderFile.absolutePath}. " +
                        "Link the iOS framework before running analyzeKmprofiler."
            )
        }

        val declarations = ObjCHeaderParser().parse(header)
        val classifier = DeclarationClassifier(externalPrefixes.get().toSet())
        val classified = declarations.map { it.copy(category = classifier.classify(it)) }

        val scanOutput = SwiftUsageScanner().scan(classified, swiftDir)
        if (scanOutput.scannedFileCount == 0 && !allowEmptyConsumerSources.get()) {
            throw GradleException(
                "No Swift source files were found in the configured swiftSourceDirs: " +
                        swiftDir.joinToString { it.absolutePath } + ". " +
                        "Set swiftSourceDirs to Swift consumer sources, or set " +
                        "allowEmptyConsumerSources = true to run a header-only audit."
            )
        }

        val unreferenced = scanOutput.unreferenced

        val result = ScanResult(
            totalExported = declarations.size,
            reviewCandidates = unreferenced.filter { it.category == DeclarationCategory.APP_CODE },
            kotlinFileFacadeCandidates = unreferenced.filter { it.category == DeclarationCategory.KOTLIN_FILE_FACADE },
            likelyExternalCandidates = unreferenced.filter {
                it.category == DeclarationCategory.LIKELY_EXTERNAL ||
                        it.category == DeclarationCategory.USER_FLAGGED_EXTERNAL
            },
            configLint = ConfigLintResult(
                transitiveExport = transitiveExport.orNull,
                isStatic = staticLinkage.orNull,
                exportedFrameworkCount = exportedFrameworkCount.orElse(1).get(),
            ),
            scannedFileCount = scanOutput.scannedFileCount,
            scannedLineCount = scanOutput.scannedLineCount,
            headerPath = resolvedHeaderFile.absolutePath,
            headerTimestamp = resolvedHeaderFile.lastModified(),
        )

        val report = MarkdownReportRenderer.render(result)
        reportOutput.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report)
        }

        logger.lifecycle("\n$report")
        logger.lifecycle("Full report written to: ${reportOutput.get().asFile.absolutePath}")
    }
}
