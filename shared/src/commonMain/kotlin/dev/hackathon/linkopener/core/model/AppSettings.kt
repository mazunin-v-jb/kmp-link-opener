package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val language: AppLanguage = AppLanguage.System,
    val autoStartEnabled: Boolean = false,
    val excludedBrowserIds: Set<BrowserId> = emptySet(),
    val browserOrder: List<BrowserId> = emptyList(),
) {
    companion object {
        val Default = AppSettings()
    }
}
