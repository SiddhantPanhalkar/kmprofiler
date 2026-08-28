// Root build is intentionally minimal.
// Plugin publication is handled by :plugin.
// Sample app configuration is handled by :sample.
plugins {
    // kotlinJvm must be on the root classpath with a known version so :plugin
    // can request org.jetbrains.kotlin.jvm without a version-compatibility clash.
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.gradlePluginPublish) apply false
}
