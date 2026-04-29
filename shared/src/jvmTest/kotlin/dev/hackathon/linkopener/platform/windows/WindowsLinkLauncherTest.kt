package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.core.model.BrowserProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsLinkLauncherTest {

    private val chrome = Browser(
        bundleId = "Google Chrome",
        displayName = "Google Chrome",
        applicationPath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
        version = null,
        family = BrowserFamily.Chromium,
    )
    private val firefox = Browser(
        bundleId = "Mozilla Firefox",
        displayName = "Mozilla Firefox",
        applicationPath = "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
        version = null,
        family = BrowserFamily.Firefox,
    )

    @Test
    fun launchesNonChromiumWithUrlAsFirstArg() = runTest {
        val recorder = ProcessRecorder(exitCode = 0)
        val launcher = WindowsLinkLauncher(processFactory = recorder)

        val ok = launcher.openIn(firefox, "https://example.com")

        assertTrue(ok)
        assertEquals(
            listOf(
                "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                "https://example.com",
            ),
            recorder.lastArgs,
        )
    }

    @Test
    fun launchesChromiumWithoutProfileAsPlainUrl() = runTest {
        // Chromium-family browser without a profile attached: same shape
        // as non-Chromium — just exe + url. Profile-less rows are how
        // single-profile Chromium installations and the
        // showBrowserProfiles=false collapse appear at the launcher.
        val recorder = ProcessRecorder(exitCode = 0)
        val launcher = WindowsLinkLauncher(processFactory = recorder)

        launcher.openIn(chrome.copy(profile = null), "https://example.com")

        assertEquals(
            listOf(chrome.applicationPath, "https://example.com"),
            recorder.lastArgs,
        )
    }

    @Test
    fun launchesChromiumWithProfileFlagBeforeUrl() = runTest {
        val recorder = ProcessRecorder(exitCode = 0)
        val launcher = WindowsLinkLauncher(processFactory = recorder)

        val withWork = chrome.copy(
            profile = BrowserProfile(id = "Profile 1", displayName = "Work"),
        )
        launcher.openIn(withWork, "https://example.com/page")

        assertEquals(
            listOf(
                chrome.applicationPath,
                "--profile-directory=Profile 1",
                "https://example.com/page",
            ),
            recorder.lastArgs,
        )
    }

    @Test
    fun nonZeroExitReturnsFalse() = runTest {
        val launcher = WindowsLinkLauncher(processFactory = ProcessRecorder(exitCode = 1))
        val ok = launcher.openIn(firefox, "https://example.com")
        assertFalse(ok)
    }

    @Test
    fun launcherSurvivesProcessFactoryThrowing() = runTest {
        val launcher = WindowsLinkLauncher(processFactory = { throw RuntimeException("nope") })
        // No crash; openIn returns false on any failure to launch.
        assertFalse(launcher.openIn(firefox, "https://example.com"))
    }

    @Test
    fun firefoxArgvFollowsOsintConvention() = runTest {
        // Firefox accepts URL as first non-flag argument; we deliberately
        // do NOT pass `-url` flag. The URL is at args[1] and Firefox
        // routes it the same way it does for `start firefox.exe URL`.
        val recorder = ProcessRecorder(exitCode = 0)
        val launcher = WindowsLinkLauncher(processFactory = recorder)

        launcher.openIn(firefox, "https://docs.mozilla.org")

        assertEquals(
            listOf(firefox.applicationPath, "https://docs.mozilla.org"),
            recorder.lastArgs,
        )
    }

    @Test
    fun urlsWithSpecialCharsArePassedVerbatim() = runTest {
        // ProcessBuilder treats each arg as a literal argv entry, so
        // `?` / `&` / `#` need no escaping. Verify the launcher doesn't
        // try to be clever with quoting.
        val recorder = ProcessRecorder(exitCode = 0)
        val launcher = WindowsLinkLauncher(processFactory = recorder)
        val url = "https://example.com/search?q=foo&bar=baz#fragment"

        launcher.openIn(firefox, url)

        assertEquals(url, recorder.lastArgs?.last())
    }

    /**
     * Records the argv list and spawns a tiny real subprocess that
     * exits with [exitCode]: `true` on POSIX returns 0, `false` returns
     * 1. We use real subprocesses because Java's `Process` is abstract
     * and uninstantiable from a test (sealed-style refusal at the JVM
     * level); the cost of spawning `true` / `false` is ~5ms per test.
     */
    private class ProcessRecorder(private val exitCode: Int) : (List<String>) -> Process {
        var lastArgs: List<String>? = null
            private set

        override fun invoke(args: List<String>): Process {
            lastArgs = args
            val cmd = if (exitCode == 0) listOf("true") else listOf("false")
            return ProcessBuilder(cmd).redirectErrorStream(true).start()
        }
    }
}
