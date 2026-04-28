package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val language: AppLanguage = AppLanguage.System,
    val autoStartEnabled: Boolean = false,
    val excludedBrowserIds: Set<BrowserId> = emptySet(),
    val browserOrder: List<BrowserId> = emptyList(),
    val manualBrowsers: List<Browser> = emptyList(),
    val rules: List<UrlRule> = emptyList(),
) {
    companion object {
        val Default = AppSettings()
    }
}
