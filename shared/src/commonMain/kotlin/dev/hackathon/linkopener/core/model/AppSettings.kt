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
    // Stage 047 toggle: when false, Chromium browsers with N≥2 profiles are
    // collapsed into a single row in `BrowserRepositoryImpl` instead of being
    // shown as N rows. Default `true` keeps post-stage-046 behaviour intact
    // for users who already saw the per-profile rows.
    val showBrowserProfiles: Boolean = true,
    // When true, a close (×) button is rendered in the picker popup header so
    // users can dismiss the picker without picking a browser or pressing Escape.
    // Defaults to false to keep the popup uncluttered for users who dismiss via
    // Escape or click-outside.
    val showCloseButton: Boolean = false,
) {
    companion object {
        val Default = AppSettings()
    }
}
