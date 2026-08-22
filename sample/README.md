# kmprofiler Sample Project

This is a minimal Kotlin Multiplatform project demonstrating how `kmprofiler` profiles the iOS
Objective-C export surface.

## What this sample demonstrates:

- **Active Code**: `UserRepository` (called from `iosApp/ContentView.swift` — filtered out)
- **Unused App Code**: `InternalSyncEngine`, `DataMapper`, `SyncListener` (flagged in Section 1)
- **Kotlin File Facades**: `DateUtilsKt` (flagged in Section 2)

## How to Run

```bash
# 1. Publish plugin locally
./gradlew :plugin:publishToMavenLocal

# 2. Run analysis on this sample
./gradlew :sample:analyzeKmprofiler
```
