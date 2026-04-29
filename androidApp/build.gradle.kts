import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.hackathon.linkopener.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hackathon.linkopener"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = providers.gradleProperty("linkopener.version").get()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            // No minify for v1 — Compose stripping rules need verification
            // first; release builds aren't on the critical path yet.
            isMinifyEnabled = false
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
            manifest.srcFile("src/main/AndroidManifest.xml")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(libs.androidx.activity.compose)
    // :shared declares these as `implementation` so they don't transitively
    // expose; the AppContainer wires them directly.
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.multiplatformSettings)
}
