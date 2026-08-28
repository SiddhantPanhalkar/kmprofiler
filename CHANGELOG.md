# Changelog

## 0.2.0

The v2 engine brings true iOS binary profiling by automatically extracting and parsing Xcode Link Maps!

### Highlights & Features:
* **True Linked Byte Attribution:** The new `./gradlew profileIosBinary` task accurately calculates how many megabytes your KMP libraries (Compose, Ktor, RevenueCat) inject into your final stripped iOS executable.
* **Automated Link Map Extraction:** No need to pollute your Xcode project settings! The plugin automatically spins up an isolated `xcodebuild` background process with `LD_GENERATE_MAP_FILE=YES` to fetch the link map.
* **Objective-C Export Cost Tracking:** Calculates exactly how many KB your ObjC wrapper classes are costing you.
* **Zero-Memory Parser:** Uses a highly efficient stream-based parsing engine that chews through 50MB+ Xcode link maps in milliseconds without bloating Gradle daemon memory.

## 0.1.1

V1 export audit release.

- Clarified that the report is based on generated Objective-C header facts and direct textual Swift matches.
- Separated observations and heuristic review candidates from proof of unused code.
- Improved candidate reporting and review guidance.
- Documented the limits of external-module classification and size conclusions.
- Added a GitHub Actions workflow for publishing from a matching release tag.

## 0.1.0

Initial release of the Kotlin Multiplatform iOS export-surface audit.

- Parses generated Objective-C headers.
- Counts exported declarations and members.
- Scans configured Swift source directories for direct name references.
- Groups review candidates into app declarations, Kotlin file facades, and likely external-module
  names.
- Reports configured linkage and framework-export settings.
