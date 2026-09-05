# Changelog

## 0.2.0

Adds iOS binary footprint profiling and comparison based on Xcode link map analysis.

### Features

* **Linked Symbol Profiling (`profileIosBinary`):** Estimates mapped live symbol bytes grouped by
  Kotlin package (e.g., `androidx.compose`, `io.ktor`).
* **Automated Link Map Extraction (`generateKmprofilerLinkMap`):** Runs a release build for the
  configured iOS destination with `LD_GENERATE_MAP_FILE=YES` and copies the generated map.
* **Objective-C Export Symbol Grouping:** Estimates the linked symbol footprint of exported
  Objective-C class, metaclass, and ivar symbols. It does not measure every bridge symbol.
* **Object-File Attribution (`attributeKmprofilerSymbols`):** Maps compiled symbols to object files
  and infers library or framework names from their paths, then reports the 50 largest symbols.
* **Baseline Comparison (`compareKmprofilerLinkMaps`):** Compares baseline and candidate link maps
  to report byte and percentage deltas across categories.
* **Streaming Link Map Parser:** Single-pass line-by-line parser that aggregates category bytes
  without retaining complete symbol lists during profiling and comparison.

## 0.1.1

V1 export audit release.

- Clarified that the report is based on generated Objective-C header facts and direct textual Swift
  matches.
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
