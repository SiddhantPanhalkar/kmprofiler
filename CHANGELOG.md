# Changelog

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
