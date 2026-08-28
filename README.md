# 🔍 kmprofiler

<p align="center">
  <a href="https://github.com/SiddhantPanhalkar/kmprofiler/actions/workflows/ci.yml"><img src="https://github.com/SiddhantPanhalkar/kmprofiler/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache_2.0-blue.svg" alt="License"></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg" alt="Kotlin"></a>
  <a href="https://gradle.org"><img src="https://img.shields.io/badge/Gradle-8.0%2B-02303A.svg" alt="Gradle"></a>
  <a href="CONTRIBUTING.md"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg" alt="PRs Welcome"></a>
  <img src="https://visitor-badge.laobi.icu/badge?page_id=SiddhantPanhalkar.kmprofiler" alt="Visitors">
</p>

<p align="center">
  <b>A zero-fabrication Gradle plugin that profiles Kotlin Multiplatform iOS export surfaces and exact binary sizes.</b><br>
  Identifies review candidates, classifies leaked transitive library symbols, and measures exactly how many Megabytes your KMP libraries are injecting into your final iOS App.
</p>

---

## 💡 The Problem

In a Kotlin Multiplatform app, profiling your iOS footprint is a challenge:

1. **Dead Code Weight:** Every `public` Kotlin declaration exported to your `.framework` gets wrapped in an Objective-C adapter. Many of these have zero direct Swift call sites in production.
2. **Transitive Leaks:** Third-party libraries (Compose Multiplatform, Ktor, RevenueCat) leak hundreds of internal wrapper types into your ObjC bridging header.
3. **Misleading Binary Sizes:** Looking at the size of your `.xcframework` file is misleading. It includes simulator slices and dead code that Xcode's Linker will strip out during final app linking.

**`kmprofiler` solves this.** It parses your iOS bridging header against your Swift code to find unreferenced wrappers, and it automates Xcode Link Map extraction to show you the *exact byte size* your Kotlin packages take up in the final iOS executable.

---

## ⚡ Two Powerful Tools

`kmprofiler` gives you two Gradle tasks depending on what you want to measure:

### 1. The Surface Auditor (`analyzeKmprofiler`)
A static analysis task that compares your `Shared.h` ObjC header against your `.swift` source code. It identifies unreferenced Kotlin declarations, file facades, and leaked library internals.

### 2. The Binary Profiler (`profileIosBinary`) ✨ *New in v0.2.0!*
Automatically runs an isolated `xcodebuild` in the background with `LD_GENERATE_MAP_FILE=YES`, extracts the Xcode Link Map from `DerivedData`, and parses every hex byte to show you exactly how many Kilobytes `androidx.compose` or `io.ktor` are adding to your final iOS App.

---

## 📦 Quickstart

### 1. Add the plugin to your version catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kmprofiler = "0.2.0"

[plugins]
kmprofiler = { id = "io.github.siddhantpanhalkar.kmprofiler", version.ref = "kmprofiler" }
```

### 2. Apply it in the shared KMP module (`build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.kmprofiler)
}

kmprofiler {
    // Required for 'analyzeKmprofiler' (Header Audit)
    headerFile.set(layout.buildDirectory.file("bin/iosArm64/releaseFramework/Shared.framework/Headers/Shared.h"))
    swiftSourceDirs.setFrom(layout.projectDirectory.dir("../iosApp"))

    // Required for 'profileIosBinary' (Binary Audit)
    iosProject.set(file("../iosApp/iosApp.xcodeproj")) // or iosWorkspace for .xcworkspace
    iosScheme.set("iosApp")

    // Optional Configs
    isStatic.set(true)
    externalPrefixes.addAll("Skiko", "Models")
    allowEmptyConsumerSources.set(false)
}
```

---

## 🚀 Running the Profilers

### The Header Auditor
```bash
./gradlew linkReleaseFrameworkIosArm64
./gradlew analyzeKmprofiler
```
*Generates `build/reports/kmprofiler-report.md`*

### The Binary Profiler
```bash
./gradlew profileIosBinary
```
*Automatically runs an iOS Release build and generates `build/reports/kmprofiler-binary-report.md`*

---

## 📊 Sample Output

### 1. iOS Binary Size Breakdown (v0.2.0)
Find out exactly what libraries are contributing to your iOS App size:

| Package / Category | Binary Size (KB) |
|---|---|
| `androidx.compose` | 9497.51 KB |
| `com.framed.app` | 1249.64 KB |
| `[iOS Export Surface]` | 1200.44 KB |
| `io.ktor` | 859.50 KB |
| `com.revenuecat` | 250.75 KB |

*(Notice `[iOS Export Surface]`? That's the exact byte cost of your Objective-C wrapper classes!)*

### 2. KMP iOS Export Profile (v0.1.0)
Review your exported declarations:

```markdown
### kmprofiler: iOS Export Profile

**Export surface:** 459 Kotlin declarations found in the generated Objective-C header.
**No standalone declaration-name token found for 282 declarations in the configured Swift sources.**

#### App-code review candidates (18)
No direct call site found in scanned Swift sources. Review for potential visibility tightening.
| Declaration | Kind | Members |
|---|---|---|
| `HomeScreenCallbacks` | class | 23 |
| `CameraState` | class | 16 |

#### Kotlin file facades — review candidates (18)
These `*Kt` classes wrap Kotlin top-level functions.
| Declaration | Members |
|---|---|
| `DimensKt` | 38 |
| `ColorKt` | 36 |
```

---

## 🛡️ Reviewing Candidates Safely

Before changing candidate visibility:

1. Check whether it is intentionally part of the public Kotlin API.
2. Verify if public and protected declarations accept, return, or inherit it.
3. Check whether `internal` satisfies the required Kotlin source sets.
4. Use declaration-level `@HiddenFromObjC` when Kotlin visibility must remain public. Note: `@HiddenFromObjC` is a declaration annotation and cannot be applied as `@file:HiddenFromObjC`.
5. After making changes, rebuild both Kotlin and iOS targets and rerun the profiler.

---

## 🛠️ Configuration Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `headerFile` | `RegularFileProperty` | Required | Path to the generated Objective-C header (`Shared.h`). |
| `swiftSourceDirs` | `ConfigurableFileCollection` | `iosApp/` | Directories or Swift files scanned for call sites. |
| `iosWorkspace` | `Property<File>` | `null` | Point to your `.xcworkspace` for automatic binary profiling. |
| `iosProject` | `Property<File>` | `null` | Point to your `.xcodeproj` (alternative to `iosWorkspace`). |
| `iosScheme` | `Property<String>` | `null` | The Xcode scheme (e.g., `iosApp`) to build for binary profiling. |
| `xcodeLinkMapFile` | `RegularFileProperty` | `null` | Provide a manual Link Map file instead of auto-generating it. |
| `frameworkBaseName` | `Property<String>` | `"Shared"` | Name defined in `binaries.framework { baseName = ... }`. |
| `isStatic` | `Property<Boolean>` | Not configured | Linkage value displayed for configuration linting. |
| `exportedFrameworkCount` | `Property<Int>` | `1` | Count of exported frameworks to flag potential binary duplication. |
| `externalPrefixes` | `ListProperty<String>` | Empty | Name prefixes grouped for ownership review (e.g. `"Skiko"`). |
| `allowEmptyConsumerSources` | `Property<Boolean>` | `false` | Allow a header-only audit when no Swift files are found. |

---

## 🎯 Scope and Roadmap

- [x] **v0.1.0** — ObjC export surface profiling, 3-tier classification, static config linting.
- [x] **v0.1.1** — Safer export audit, token-aware Swift matching, scan provenance, and conservative review guidance.
- [x] **v0.2.0** — Automated Xcode Link Map parsing for true app-binary linked byte attribution.
- [ ] **v0.3.0** — Baseline export diffing & CI regression gating.

---

## 📄 License & Contributing

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

Contributions are warmly welcomed! Feel free to open an issue or submit a pull request.
