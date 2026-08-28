# kmprofiler sample project

This small Kotlin Multiplatform project shows the V1 export-surface audit. The report contains facts
from the generated header and review candidates based on direct textual matches in the sample Swift
source. A candidate is not proof that a Kotlin declaration is unused.

The sample includes one declaration referenced by `iosApp/ContentView.swift`, a few app declarations
with no matching Swift type token, and a Kotlin file facade. The referenced declaration is omitted
from the candidate list; the other declarations are included for review.

## Run

From the repository root:

```bash
./gradlew :plugin:publishToMavenLocal
./gradlew :sample:analyzeKmprofiler
```

The sample uses the plugin version configured in `sample/build.gradle.kts` and resolves it from
Maven Local.
