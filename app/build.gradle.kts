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
        versionCode = 7
        versionName = "0.7.0"
    }

    // Release builds are signed with the project key (release/kingshot.p12, protected by the KS_PASSWORD secret) so that in-app updates
    // install over earlier releases. Without the secret (local or fork builds) release falls back to the debug key and cannot update a release install.
    signingConfigs {
        create("release") {
            val password = System.getenv("KS_PASSWORD")
            if (password != null) {
                storeFile = rootProject.file("release/kingshot.p12")
                storeType = "pkcs12"
                storePassword = password
                keyAlias = "kingshot"
                keyPassword = password
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KS_PASSWORD") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    implementation(libs.compose.animation.core)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)

    // JVM unit tests for the pure logic (matcher, planner, report, ...). No runtime dependencies.
    testImplementation(libs.junit)
    // Test-only (Apache-2.0): reads recorded device fixtures (fixtures/capabilities/*.json).
    testImplementation(libs.gson)
}
