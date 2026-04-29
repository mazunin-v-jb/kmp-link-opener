package dev.hackathon.linkopener.platform.windows

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsDefaultBrowserServiceTest {

    @Test
    fun isDefaultBrowserTrueWhenProgIdMatches() = runTest {
        val service = WindowsDefaultBrowserService { "LinkOpener.URL" }
        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserTrueWhenStartMenuKeyReturned() = runTest {
        // Chrome's elevation service can reset UserChoice; Windows then falls
        // back to the StartMenuInternet sub-key name as the effective ProgId.
        val service = WindowsDefaultBrowserService { "LinkOpener" }
        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserFalseWhenProgIdIsAnotherApp() = runTest {
        val service = WindowsDefaultBrowserService { "ChromeHTML" }
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserFalseWhenResolverReturnsNull() = runTest {
        val service = WindowsDefaultBrowserService { null }
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserMatchIsCaseInsensitive() = runTest {
        val service = WindowsDefaultBrowserService { "LINKOPENER.URL" }
        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun canOpenSystemSettingsTrue() {
        val service = WindowsDefaultBrowserService { null }
        assertEquals(true, service.canOpenSystemSettings)
    }
}
