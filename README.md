# kmprofiler

<p align="center">
  <a href="https://github.com/SiddhantPanhalkar/kmprofiler/actions/workflows/ci.yml"><img src="https://github.com/SiddhantPanhalkar/kmprofiler/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg" alt="Kotlin"></a>
  <a href="https://gradle.org"><img src="https://img.shields.io/badge/Gradle-8.0%2B-02303A.svg" alt="Gradle"></a>
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg" alt="PRs Welcome"></a>
  <img src="https://visitor-badge.laobi.icu/badge?page_id=SiddhantPanhalkar.kmprofiler" alt="Visitors">
</p>

<p align="center">
  <b>A Gradle plugin for auditing Kotlin Multiplatform iOS export surfaces and measuring linked binary size.</b><br>
  Finds Objective-C declarations for review and groups linked Kotlin symbols in your iOS app.
</p>

---

## The problem

Profiling the iOS footprint of a Kotlin Multiplatform project involves two different problems:

1. **Objective-C export surface:** Public Kotlin declarations generate Objective-C adapters in the
   framework header. As a project grows, unused declarations, Kotlin file facades, and transitive
   library types inflate this surface.
2. **Binary footprint:** Inspecting the file size of a `.framework` or `.xcframework` does not show
   what ships in the final app. Xcode linkers strip dead code during app linking.

`kmprofiler` addresses both problems:

- It audits the generated Objective-C header against your Swift source files to find declarations
  without call sites.
- It automates Xcode link map extraction to measure live linked symbols and group them by Kotlin
  package and inferred object-file or library name.

---

## Tasks

| Task                         | Description                                                                                                           |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `analyzeKmprofiler`          | Audits the generated Objective-C header against Swift sources and reports declarations for review.                    |
| `generateKmprofilerLinkMap`  | Builds the iOS app with `LD_GENERATE_MAP_FILE=YES` and copies the link map to `build/reports/kmprofiler-linkmap.txt`. |
| `profileIosBinary`           | Measures total mapped symbol size and groups symbols by Kotlin package and export surface.                            |
| `attributeKmprofilerSymbols` | Attributes mapped symbols to object files and inferred libraries or frameworks, then lists the 50 largest symbols.     |
| `compareKmprofilerLinkMaps`  | Compares two link maps and reports size deltas across packages and categories between builds.                         |

---

## Quickstart

### 1. Add the plugin to your version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kmprofiler = "0.2.0"

[plugins]
kmprofiler = { id = "io.github.siddhantpanhalkar.kmprofiler", version.ref = "kmprofiler" }
```

### 2. Apply it in your shared KMP module (`build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.kmprofiler)
}

kmprofiler {
    // Export surface audit settings
    headerFile.set(layout.buildDirectory.file("bin/iosArm64/releaseFramework/Shared.framework/Headers/Shared.h"))
    swiftSourceDirs.setFrom(layout.projectDirectory.dir("../iosApp"))

    // Xcode binary profiling settings
    iosProject.set(file("../iosApp/iosApp.xcodeproj"))
    iosScheme.set("iosApp")

    // Configuration checks and ownership review
    isStatic.set(true)
    externalPrefixes.addAll("Skiko", "Models")
}
```

---

## Usage

The commands below assume that you run Gradle from the module that applies the plugin. If you run
Gradle from the repository root, add that module's path to each task, for example
`./gradlew :shared:profileIosBinary`.

### Auditing the Objective-C export surface

Build the release framework first. This is a separate Gradle invocation because the audit reads the
header produced by the framework build:

```bash
./gradlew linkReleaseFrameworkIosArm64
```

Then run the audit as a second invocation:

```bash
./gradlew analyzeKmprofiler
```

The task reads the header, scans your configured Swift files for standalone type tokens, and writes
review candidates to `build/reports/kmprofiler-report.md`. A candidate has no matching token in the
scanned Swift sources; it is not proof that the declaration is unused.

### Generating a link map

To generate only the link map, run:

```bash
./gradlew generateKmprofilerLinkMap
```

This runs a clean Xcode `Release` build for the configured destination with
`LD_GENERATE_MAP_FILE=YES`. By default, the destination is `generic/platform=iOS`. The generated
map is copied to:

```text
build/reports/kmprofiler-linkmap.txt
```

The task needs `iosProject` or `iosWorkspace` and `iosScheme` in the `kmprofiler {}` configuration.
It does not use a simulator unless you explicitly configure an appropriate Xcode destination.

### Measuring the mapped binary footprint

Run:

```bash
./gradlew profileIosBinary
```

When `iosScheme` is configured, this task depends on `generateKmprofilerLinkMap`, so the link map is
generated before analysis. The task then streams through the map and writes:

```text
build/reports/kmprofiler-binary-report.md
```

If you already have a map, set `xcodeLinkMapFile` in the `kmprofiler {}` block. In this mode,
`profileIosBinary` reads that file and does not run Xcode. You must configure either `iosScheme`
for automatic generation or `xcodeLinkMapFile` for manual input.

### Attributing symbols to object files and libraries

Run the attribution task:

```bash
./gradlew attributeKmprofilerSymbols
```

When `iosScheme` is configured, this task also depends on `generateKmprofilerLinkMap`. With
`xcodeLinkMapFile`, it reads the supplied map instead. The task maps each live symbol to its object
file and infers a library or framework name from the path. It writes a breakdown and the 50 largest
symbols to:

```text
build/reports/kmprofiler-attribution.md
```

### Comparing link maps across builds

Comparison uses two maps that you create separately. Build the baseline and candidate with the same
Release configuration, scheme, destination, and architecture. This keeps the comparison focused
on code changes rather than build settings.

For each build, run `generateKmprofilerLinkMap` and copy the generated file before running the next
build. For example:

```bash
mkdir -p linkmaps
./gradlew generateKmprofilerLinkMap
cp build/reports/kmprofiler-linkmap.txt linkmaps/baseline.txt

# Make the code or build change, then generate the candidate map.
./gradlew generateKmprofilerLinkMap
cp build/reports/kmprofiler-linkmap.txt linkmaps/candidate.txt
```

Configure those saved files in your `kmprofiler {}` block:

```kotlin
kmprofiler {
    baselineLinkMap.set(layout.projectDirectory.file("linkmaps/baseline.txt"))
    candidateLinkMap.set(layout.projectDirectory.file("linkmaps/candidate.txt"))
}
```

Then run:

```bash
./gradlew compareKmprofilerLinkMaps
```

The task reads the two configured files and writes byte and percentage deltas to
`build/reports/kmprofiler-comparison.md`. It does not generate the baseline or candidate maps, set a
threshold, or fail CI when a size increases.

---

## Sample reports

These examples come from profiling `framed-app`, a real Kotlin Multiplatform project.

### Export surface audit (`analyzeKmprofiler`)

```markdown
### kmprofiler: iOS Export Profile

**Export surface:** 259 declarations found in the generated Objective-C header.
**No direct Swift call site found for 244 of them.**

#### Your code: review candidates (96)

| Declaration | Kind | Members | Confidence | Remediation | Evidence |
|---|---|---|---|---|---|
| `HomeViewModel` | class | 55 | high | manual review only | none |
| `FilterRecipe` | class | 13 | high | manual review only | none |
| `CameraController` | protocol | 16 | high | manual review only | none |

#### Kotlin file facades: review candidates (13)

| Declaration | Members | Confidence | Remediation | Evidence |
|---|---|---|---|---|
| `AppModuleKt` | 7 | high | manual review only | none |
| `LutGeneratorKt` | 2 | high | manual review only | none |
```

### Binary size breakdown (`profileIosBinary`)

```markdown
### kmprofiler: iOS Binary Size Breakdown

#### Summary

| Metric | Value |
|:---|---:|
| Total Symbols | 333,144 |
| Classified Symbols | 31,392 |
| Total Mapped Symbol Size | 49.34 MB |
| Total Classified Mapped Size | 18.12 MB (36.7%) |
| Unclassified Mapped Symbols | 31.22 MB (63.3%) |

#### Package / Category Breakdown

| Package / Category | Binary Size | % of Total |
|:---|---:|---:|
| `androidx.compose` | 9.29 MB | 18.8% |
| `framed.shared` | 1.81 MB | 3.7% |
| `com.framed` | 1.49 MB | 3.0% |
| `io.ktor` | 856.70 KB | 1.7% |
| `kotlinx.serialization` | 516.61 KB | 1.0% |
| `[iOS Export Surface]` | 22.58 KB | < 0.1% |
```

### Symbol attribution (`attributeKmprofilerSymbols`)

```markdown
### kmprofiler: Symbol Attribution

#### Object-file and library breakdown

| Object-file or library name | Mapped Size | Symbols | % of Total |
|:---|---:|---:|---:|
| `Shared` | 46.25 MB | 276,206 | 93.7% |
| `GoogleAppMeasurement` | 980.52 KB | 12,064 | 1.9% |
| `unknown` | 700.76 KB | 16,477 | 1.4% |
| `FirebaseCrashlytics` | 255.06 KB | 4,074 | 0.5% |
| `FirebaseSharedSwift` | 169.03 KB | 1,773 | 0.3% |
```

---

## Reviewing candidates safely

A declaration without a Swift call site is not necessarily dead code. It may be called through:

- Kotlin code in common or platform source sets
- Dependency injection or reflection
- Objective-C files not included in the Swift source scan
- Protocols or dynamic dispatch

Before removing or hiding a declaration:

1. Check if the declaration is intentionally part of your public Kotlin API.
2. Check if other public declarations accept, return, or inherit it.
3. If it is only needed in Kotlin, consider changing its visibility to `internal` or `private`.
4. If it must stay public in Kotlin but is not needed from Swift or Objective-C, annotate it with
   `@HiddenFromObjC`.
5. For top-level functions in generated `*Kt` facades, annotate individual declarations with
   `@HiddenFromObjC`. The annotation applies to declarations, not files.

After making changes, re-link your framework and run `analyzeKmprofiler` again to verify the header
difference.

---

## Measurements and heuristics

The plugin clearly separates direct measurements from heuristics:

- **Measured from the link map:** Total mapped symbol size, individual symbol sizes, and object file
  counts come directly from the link map `# Symbols:` section.
- **Estimated or grouped:** Kotlin package attribution relies on parsing mangled symbol names (such
  as `_kfun:`). Attribution maps object file numbers back to the `# Object files:` list and infers
  library or framework names from those paths. Objective-C export surface size estimates the linked
  byte contribution of exported classes, metaclasses, and ivars (`_OBJC_CLASS_`,
  `_OBJC_METACLASS_`, `_OBJC_IVAR_`). It does not represent every Objective-C method, selector, or
  piece of bridge metadata.
- **Link map size vs app file size:** The total mapped symbol size (49.34 MB in the sample above)
  represents discrete compiled functions and static data. The final Mach-O executable on disk is
  larger (81.77 MB for the same build) because it includes Mach-O headers, dynamic loader tables,
  code signatures, and alignment padding.

---

## Configuration reference

| Property                    | Type                         | Default                | Why it exists                                                                                    |
|-----------------------------|------------------------------|------------------------|--------------------------------------------------------------------------------------------------|
| `headerFile`                | `RegularFileProperty`        | Required               | Points to the generated Objective-C header (`Shared.h`) for export surface auditing.             |
| `swiftSourceDirs`           | `ConfigurableFileCollection` | `iosApp/`              | Defines which Swift directories to scan for declaration call sites.                              |
| `iosWorkspace`              | `Property<File>`             | `null`                 | Points to your `.xcworkspace` if your iOS project uses workspaces or CocoaPods.                  |
| `iosProject`                | `Property<File>`             | `null`                 | Points to your `.xcodeproj` if your iOS project does not use a workspace.                        |
| `iosScheme`                 | `Property<String>`           | `null`                 | Specifies the Xcode scheme to build for automated link map generation.                           |
| `xcodeConfiguration`        | `Property<String>`           | `Release`              | Selects the Xcode build configuration so profiling reflects optimized release binaries.          |
| `sdkDestination`            | `Property<String>`           | `generic/platform=iOS` | Sets the target platform destination passed to `xcodebuild`.                                     |
| `architecture`              | `Property<String>`           | Auto-detected          | Overrides architecture resolution when building multi-architecture binaries.                     |
| `derivedDataPath`           | `Property<File>`             | `null`                 | Isolates Xcode build outputs to a dedicated folder instead of default DerivedData.               |
| `xcconfig`                  | `Property<File>`             | `null`                 | Passes custom build settings into `xcodebuild` during link map generation.                       |
| `xcodeTimeoutMinutes`       | `Property<Int>`              | `30`                   | Prevents hanging builds by setting an execution time limit on `xcodebuild`.                      |
| `xcodeLinkMapFile`          | `RegularFileProperty`        | `null`                 | Allows analyzing an existing link map without triggering a new Xcode build.                      |
| `baselineLinkMap`           | `RegularFileProperty`        | `null`                 | Sets the reference link map file when comparing size changes.                                    |
| `candidateLinkMap`          | `RegularFileProperty`        | `null`                 | Sets the new link map file when comparing size changes.                                          |
| `frameworkBaseName`         | `Property<String>`           | `"Shared"`             | Matches the framework name configured in Kotlin/Native `binaries.framework`.                     |
| `frameworkPrefix`           | `Property<String>`           | `"Shared"`             | Matches the symbol prefix used by Kotlin/Native for exported Objective-C types.                  |
| `isStatic`                  | `Property<Boolean>`          | Not set                | Records linkage in the audit report to help determine if linker dead-stripping applies.          |
| `exportedFrameworkCount`    | `Property<Int>`              | `1`                    | Flags potential duplication if multiple independent frameworks share dependencies.               |
| `externalPrefixes`          | `ListProperty<String>`       | Empty                  | Groups matching names for ownership review. It does not ignore those declarations or remove them. |
| `allowEmptyConsumerSources` | `Property<Boolean>`          | `false`                | Prevents the audit from failing when no Swift files exist, treating all exports as unreferenced. |

---

## Scope and roadmap

- [x] **v0.1.0**: Objective-C export surface profiling, 3-tier classification, and static
  configuration linting.
- [x] **v0.1.1**: Token-aware Swift matching, scan provenance, and conservative review guidance.
- [x] **v0.2.0**: Automated Xcode link map generation, streaming symbol profiler, module
  attribution, and baseline comparison.
- [ ] **v0.3.0**: Deeper Kotlin compiler graph analysis and public API exposure tracing.

---

## Development

```bash
./gradlew :plugin:test
./gradlew :plugin:publishToMavenLocal
```

See the sample project in [`sample/`](sample/) for a minimal configuration.

---

## License and contributing

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

Contributions are welcome. Open an issue or submit a pull request with focused changes and tests.
