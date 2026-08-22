# 🚀 kmprofiler v0.1.0 — Initial Release

Zero-fabrication Gradle plugin that profiles Kotlin Multiplatform iOS Objective-C export surfaces.

### ✨ Highlights & Features:

* **Objective-C Export Analysis:** Parses generated `Shared.h` headers and extracts all exported
  classes, protocols, and member counts.
* **Swift Call-Site Detection:** Scans your Swift source files to find unreferenced Kotlin
  declarations.
* **3-Tier Categorization:**
    * **App Code Candidates:** Highlights unused app classes & protocols sorted by member count.
    * **Kotlin File Facades (`*Kt`):** Identifies top-level Kotlin functions wrapped in generated
      `*Kt` classes (actionable via `@file:HiddenFromObjC`).
    * **Transitive Library Internals:** Detects leaked symbols from libraries (`Ktor_`, `Koin_`,
      `Ui_`, etc.).
* **Static Config Linting:** Diagnoses `transitiveExport`, static linkage (`isStatic`), and
  multi-framework binary duplication.
* **Actionable Report:** Outputs an honest Markdown report to `build/reports/kmprofiler-report.md`.

---

### 📦 Quickstart

Add to `gradle/libs.versions.toml`:

```toml
[versions]
kmprofiler = "0.1.0"

[plugins]
kmprofiler = { id = "io.github.siddhantpanhalkar.kmprofiler", version.ref = "kmprofiler" }
```

Apply in your shared module `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kmprofiler)
}

kmprofiler {
    headerFile.set(layout.buildDirectory.file("bin/iosArm64/releaseFramework/Shared.framework/Headers/Shared.h"))
    swiftSourceDirs.setFrom(layout.projectDirectory.dir("../iosApp"))
    isStatic.set(true)
}
```

Run in terminal:

```bash
./gradlew analyzeKmprofiler
```
