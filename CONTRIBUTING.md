# Contributing to kmprofiler

Thank you for contributing to `kmprofiler`!

## Setup

```bash
git clone https://github.com/SiddhantPanhalkar/kmprofiler.git
cd kmprofiler
./gradlew :plugin:test
```

## Verification

Before submitting a PR:

```bash
./gradlew :plugin:test
./gradlew :plugin:publishToMavenLocal
./gradlew :sample:analyzeKmprofiler
```

## Guidelines

- Include unit tests for parser and classifier changes.
- Follow Kotlin coding conventions.
