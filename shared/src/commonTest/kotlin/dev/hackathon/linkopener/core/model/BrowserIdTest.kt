package dev.hackathon.linkopener.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserIdTest {

    private fun chrome(profile: BrowserProfile? = null) = Browser(
        bundleId = "com.google.Chrome",
        displayName = "Google Chrome",
        applicationPath = "/Applications/Google Chrome.app",
        version = "131.0",
        profile = profile,
        family = BrowserFamily.Chromium,
    )

    @Test
    fun browserWithoutProfileUsesBareApplicationPath() {
        // Backward compat: persisted ids from before stage 046 stay valid as
        // long as the browser doesn't have profiles attached.
        val id = chrome().toBrowserId()
        assertEquals(BrowserId("/Applications/Google Chrome.app"), id)
    }

    @Test
    fun browserWithProfileSuffixesIdWithHash() {
        val id = chrome(BrowserProfile(id = "Profile 1", displayName = "Work")).toBrowserId()
        assertEquals(BrowserId("/Applications/Google Chrome.app#Profile 1"), id)
    }

    @Test
    fun differentProfilesProduceDistinctIds() {
        val work = chrome(BrowserProfile("Profile 1", "Work")).toBrowserId()
        val personal = chrome(BrowserProfile("Default", "Personal")).toBrowserId()
        // Each profile is a first-class participant — exclusion toggle on one
        // must not affect the other.
        assertEquals(false, work == personal)
    }

    @Test
    fun defaultProfileIsTreatedAsAnyOther() {
        // No special-casing of "Default" — it gets the same #-suffix form.
        val id = chrome(BrowserProfile("Default", "Personal")).toBrowserId()
        assertEquals(BrowserId("/Applications/Google Chrome.app#Default"), id)
    }
}
