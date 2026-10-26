// AGP 9 has built-in Kotlin, so no separate Kotlin Gradle plugin is applied.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose) // Compose compiler; version = Kotlin version
    alias(libs.plugins.ktlint) // style gate; MIT-licensed build tool, no runtime dependency
}

android {
    namespace = "com.kingshot.camera"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kingshot.camera" // placeholder until the name check (docs/ROADMAP.md)
        minSdk = 34
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Lab build only: signed with the debug key. Replace before any release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Phase 1 UI: Compose foundation + ui only (no Material library), ViewModel/StateFlow, Preferences DataStore for settings.
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)

    // JVM unit tests for the pure logic (matcher, planner, report, ...). No runtime dependencies.
    testImplementation(libs.junit)
    // Test-only (Apache-2.0): reads recorded device fixtures (fixtures/capabilities/*.json).
    testImplementation(libs.gson)
}
