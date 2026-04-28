package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.BrowserFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromiumUserDataDirsTest {

    @Test
    fun chromeIdentifiedAsChromium() {
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.google.Chrome"))
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.google.Chrome.beta"))
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.google.Chrome.canary"))
    }

    @Test
    fun edgeIdentifiedAsChromium() {
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.microsoft.Edge"))
    }

    @Test
    fun braveIdentifiedAsChromium() {
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.brave.Browser"))
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.brave.Browser.beta"))
    }

    @Test
    fun vivaldiAndOperaIdentifiedAsChromium() {
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.vivaldi.Vivaldi"))
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.operasoftware.Opera"))
        assertEquals(BrowserFamily.Chromium, detectBrowserFamily("com.operasoftware.OperaGX"))
    }

    @Test
    fun firefoxIdentifiedAsFirefox() {
        assertEquals(BrowserFamily.Firefox, detectBrowserFamily("org.mozilla.firefox"))
        // Firefox-specific developer / nightly variants share the prefix.
        assertEquals(BrowserFamily.Firefox, detectBrowserFamily("org.mozilla.firefox.developer"))
    }

    @Test
    fun safariIdentifiedAsSafari() {
        assertEquals(BrowserFamily.Safari, detectBrowserFamily("com.apple.Safari"))
        // Safari Technology Preview also matches the prefix.
        assertEquals(BrowserFamily.Safari, detectBrowserFamily("com.apple.SafariTechnologyPreview"))
    }

    @Test
    fun unknownBundleIdReturnsOther() {
        assertEquals(BrowserFamily.Other, detectBrowserFamily("com.example.unknown"))
        assertEquals(BrowserFamily.Other, detectBrowserFamily(""))
        assertEquals(BrowserFamily.Other, detectBrowserFamily("com.duckduckgo.macos.browser"))
    }

    @Test
    fun userDataPathLookupReturnsKnownChromiumDir() {
        assertEquals("Google/Chrome", chromiumUserDataPaths["com.google.Chrome"])
        assertEquals("Microsoft Edge", chromiumUserDataPaths["com.microsoft.Edge"])
        assertEquals("BraveSoftware/Brave-Browser", chromiumUserDataPaths["com.brave.Browser"])
    }

    @Test
    fun userDataPathLookupNullForNonChromium() {
        assertEquals(null, chromiumUserDataPaths["org.mozilla.firefox"])
        assertEquals(null, chromiumUserDataPaths["com.apple.Safari"])
    }
}
