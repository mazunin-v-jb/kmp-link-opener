package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Browser(
    val bundleId: String,
    val displayName: String,
    val applicationPath: String,
    val version: String?,
    // Chromium-family browsers with N≥2 user profiles are expanded into one
    // Browser per profile during discovery (stage 046). For non-profile rows
    // (single-profile Chromium, manual-add, Safari, ...) `profile` stays null.
    val profile: BrowserProfile? = null,
    // Set during discovery so the launcher knows which CLI flag to use.
    // Defaults to null for backward compat with persisted data and for
    // manual-add entries (we don't probe family on manual-add — the user
    // says the .app handles links and we trust them).
    val family: BrowserFamily? = null,
)

/**
 * User-visible label that combines the parent browser name with the profile
 * name, em-dash style: "Google Chrome — Work". For browsers without a profile
 * it's just [Browser.displayName]. Single source of truth so the picker, the
 * Settings row, and the Rules-section dropdown all show the same string.
 */
val Browser.uiLabel: String
    get() = if (profile != null) "$displayName — ${profile.displayName}" else displayName
