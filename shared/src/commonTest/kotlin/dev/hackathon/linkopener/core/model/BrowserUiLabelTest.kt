package dev.hackathon.linkopener.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Single source of truth for the user-visible name shown in Settings rows,
 * the picker, and the Rules dropdown. The format must stay stable: the em
 * dash (` — `) is the visual separator the design system relies on, and
 * fall-back to the bare display name when no profile is attached is what
 * non-Chromium / single-profile browsers depend on.
 */
class BrowserUiLabelTest {

    private val baseChromium = Browser(
        bundleId = "com.google.Chrome",
        displayName = "Google Chrome",
        applicationPath = "/Applications/Google Chrome.app",
        version = "131",
        family = BrowserFamily.Chromium,
    )

    @Test
    fun returnsDisplayNameWhenNoProfile() {
        val safari = Browser(
            bundleId = "com.apple.Safari",
            displayName = "Safari",
            applicationPath = "/Applications/Safari.app",
            version = "17.4",
        )

        assertEquals("Safari", safari.uiLabel)
    }

    @Test
    fun combinesDisplayNameAndProfileNameWithEmDash() {
        val withWork = baseChromium.copy(profile = BrowserProfile(id = "Profile 1", displayName = "Work"))

        assertEquals("Google Chrome — Work", withWork.uiLabel)
    }

    @Test
    fun usesProfileDisplayNameNotProfileId() {
        // The id is the directory name on disk ("Profile 1"); the displayName
        // is the user-friendly label from Local State. Picker and Settings
        // must surface the friendly name, not the disk id.
        val withFriendly = baseChromium.copy(
            profile = BrowserProfile(id = "Profile 7", displayName = "Personal"),
        )

        assertEquals("Google Chrome — Personal", withFriendly.uiLabel)
    }

    @Test
    fun nullProfileFallsBackToDisplayNameEvenWhenFamilyIsChromium() {
        // Single-profile Chromium installations carry family=Chromium but
        // profile=null (we don't dilute the picker with synthetic "Default"
        // entries — see plan §Q2). uiLabel should still be the bare name.
        val singleProfileChrome = baseChromium.copy(profile = null)

        assertEquals("Google Chrome", singleProfileChrome.uiLabel)
    }

    @Test
    fun preservesUnicodeAndPunctuationInDisplayNames() {
        val odd = Browser(
            bundleId = "x",
            displayName = "Brave Beta — Test",
            applicationPath = "/Applications/Brave Beta.app",
            version = "1",
            profile = BrowserProfile(id = "p", displayName = "ジョン"),
        )

        // Even though the parent name already contains an em-dash, the
        // separator-and-profile suffix is appended verbatim — uiLabel
        // doesn't try to be smart about pre-existing punctuation.
        assertEquals("Brave Beta — Test — ジョン", odd.uiLabel)
    }
}
