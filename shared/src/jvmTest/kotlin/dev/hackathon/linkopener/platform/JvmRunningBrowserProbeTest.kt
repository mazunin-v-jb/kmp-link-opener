package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.toBrowserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class JvmRunningBrowserProbeTest {

    private val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4")
    private val chrome = Browser("com.google.Chrome", "Chrome", "/Applications/Google Chrome.app", "124")
    private val firefoxWin = Browser(
        "Firefox", "Firefox",
        "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
        "125",
    )

    @Test
    fun macOsMatchesByBundlePathPrefix() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = {
                listOf(
                    "/Applications/Safari.app/Contents/MacOS/Safari",
                    "/Applications/SomeOther.app/Contents/MacOS/Other",
                    "/usr/bin/zsh",
                )
            },
        )

        val running = probe.runningOf(listOf(safari, chrome))

        assertEquals(setOf(safari.toBrowserId()), running)
    }

    @Test
    fun macOsMatchesAllInstalledWhenAllAreRunning() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = {
                listOf(
                    "/Applications/Safari.app/Contents/MacOS/Safari",
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                )
            },
        )

        val running = probe.runningOf(listOf(safari, chrome))

        assertEquals(setOf(safari.toBrowserId(), chrome.toBrowserId()), running)
    }

    @Test
    fun macOsReturnsEmptyWhenNothingMatches() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = { listOf("/usr/sbin/syslogd", "/sbin/launchd") },
        )

        val running = probe.runningOf(listOf(safari, chrome))

        assertEquals(emptySet(), running)
    }

    @Test
    fun macOsRejectsXpcServiceHelpersUnderTheSameBundle() = runTest {
        // Safari spawns background XPC services (SafeBrowsing, History sync,
        // BookmarksSyncAgent) under `${bundle}/Contents/XPCServices/`. They
        // get launched by launchd even when Safari proper is closed. The
        // probe must NOT mark Safari as running based on those alone — a
        // strict `${bundle}/Contents/MacOS/` prefix excludes them while
        // still matching the real main process.
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = {
                listOf(
                    "/Applications/Safari.app/Contents/XPCServices/com.apple.Safari.SafeBrowsing.Service.xpc/Contents/MacOS/com.apple.Safari.SafeBrowsing.Service",
                    "/Applications/Safari.app/Contents/XPCServices/com.apple.Safari.History.xpc/Contents/MacOS/com.apple.Safari.History",
                )
            },
        )

        val running = probe.runningOf(listOf(safari))

        assertEquals(emptySet(), running)
    }

    @Test
    fun macOsRejectsChromiumFrameworkHelpers() = runTest {
        // Chrome / Edge / Brave / Opera / Vivaldi all spawn renderer / GPU /
        // utility helpers under `${bundle}/Contents/Frameworks/.../Helpers/`.
        // They typically die with the browser, but lingering ones must not
        // alone mark the browser as running.
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = {
                listOf(
                    "/Applications/Google Chrome.app/Contents/Frameworks/Google Chrome Framework.framework/Versions/124.0.6367.91/Helpers/Google Chrome Helper.app/Contents/MacOS/Google Chrome Helper",
                )
            },
        )

        val running = probe.runningOf(listOf(chrome))

        assertEquals(emptySet(), running)
    }

    @Test
    fun windowsMatchesByExactExecutablePath() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Windows,
            runCommand = {
                listOf(
                    "C:\\Windows\\System32\\svchost.exe",
                    "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
                )
            },
        )

        val running = probe.runningOf(listOf(firefoxWin))

        assertEquals(setOf(firefoxWin.toBrowserId()), running)
    }

    @Test
    fun windowsMatchIsCaseInsensitive() = runTest {
        // Windows file paths are case-preserving but case-insensitive — a
        // running process reported with lowercased components must still
        // match a Browser whose path uses canonical casing.
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Windows,
            runCommand = { listOf("c:\\program files\\mozilla firefox\\FIREFOX.exe") },
        )

        val running = probe.runningOf(listOf(firefoxWin))

        assertEquals(setOf(firefoxWin.toBrowserId()), running)
    }

    @Test
    fun androidHostShortCircuitsToEmpty() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Android,
            runCommand = { error("runCommand must not be invoked on Android host") },
        )

        val running = probe.runningOf(listOf(safari))

        assertEquals(emptySet(), running)
    }

    @Test
    fun emptyInstalledListReturnsEmpty() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.MacOs,
            runCommand = { listOf("/Applications/Safari.app/Contents/MacOS/Safari") },
        )

        val running = probe.runningOf(emptyList())

        assertEquals(emptySet(), running)
    }
}
