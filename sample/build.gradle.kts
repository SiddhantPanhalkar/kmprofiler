plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("io.github.siddhantpanhalkar.kmprofiler") version "0.1.0"
}

kotlin {
    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    android {
        namespace = "io.github.siddhantpanhalkar.sample"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    sourceSets {
        commonMain.dependencies {}
    }
}

kmprofiler {
    headerFile.set(
        layout.buildDirectory.file(
            "bin/iosArm64/debugFramework/Shared.framework/Headers/Shared.h"
        )
    )
    swiftSourceDirs.setFrom(layout.projectDirectory.dir("iosApp"))
    isStatic.set(true)
    exportedFrameworkCount.set(1)
}

tasks.named("analyzeKmprofiler") {
    dependsOn("linkDebugFrameworkIosArm64")
}
