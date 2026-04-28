import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatformSettings)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

kover {
    reports {
        filters {
            excludes {
                // UI surface — exercised manually / by stage 4.5 design system, not unit-tested.
                classes(
                    "dev.hackathon.linkopener.ui.icons.**",
                    "dev.hackathon.linkopener.ui.settings.SettingsScreen*",
                    "dev.hackathon.linkopener.ui.theme.LinkOpenerTheme*",
                    "dev.hackathon.linkopener.ui.theme.LinkOpenerColors*",
                    "dev.hackathon.linkopener.ui.theme.LinkOpenerTypography*",
                    "dev.hackathon.linkopener.ui.tray.**",
                )
                // Process-spawning / framework-glue layers we'd only smoke-test on macOS.
                classes(
                    "dev.hackathon.linkopener.platform.JvmUrlReceiver*",
                    "dev.hackathon.linkopener.platform.macos.PlutilRunner*",
                    "dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager*",
                )
            }
        }
    }
}
