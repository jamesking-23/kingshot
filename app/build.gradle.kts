// AGP 9 has built-in Kotlin, so no separate Kotlin Gradle plugin is applied.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint) // style gate; MIT-licensed build tool, no runtime dependency
}

android {
    namespace = "com.kingshot.camera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingshot.camera" // placeholder until the name check (docs/ROADMAP.md)
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
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
    // JVM unit tests for the pure logic (matcher, planner, report, ...). No runtime dependencies.
    testImplementation(libs.junit)
}
