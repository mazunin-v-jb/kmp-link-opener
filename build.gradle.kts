plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.compose.multiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.kover).apply(false)
    // Stage 09 Android port — registered at root so :shared and :androidApp
    // can apply them without re-declaring the version. Kept .apply(false)
    // here; consumed in :shared (library) and :androidApp (application).
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.kotlin.android).apply(false)
}
