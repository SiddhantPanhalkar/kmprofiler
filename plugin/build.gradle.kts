plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.gradlePluginPublish)
    `java-gradle-plugin`
    `maven-publish`
    jacoco
}

group = "io.github.siddhantpanhalkar"
version = "0.2.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(gradleApi())

    testImplementation(gradleTestKit())
    // BOM required: junit-jupiter / junit-platform-launcher have no version in the catalog.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.platform.junit.platform.launcher)
}

gradlePlugin {
    website.set("https://github.com/SiddhantPanhalkar/kmprofiler")
    vcsUrl.set("https://github.com/SiddhantPanhalkar/kmprofiler.git")

    plugins {
        create("kmprofilerPlugin") {
            id = "io.github.siddhantpanhalkar.kmprofiler"
            displayName = "kmprofiler"
            description = "Kotlin Multiplatform iOS export and link map profiler"
            tags.set(listOf("kotlin", "kmp", "ios", "objective-c", "profiler", "xcframework"))
            implementationClass = "io.github.siddhantpanhalkar.kmprofiler.KmprofilerPlugin"
        }
    }
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
