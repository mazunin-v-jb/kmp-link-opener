package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.toBrowserId
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class JvmRunningBrowserProbeTest {

    @get:Rule
    val tmp = TemporaryFolder()

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
    fun androidHostReturnsNullToSignalUnsupported() = runTest {
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Android,
            runCommand = { error("runCommand must not be invoked on Android host") },
        )

        // null = "host doesn't support probing" — picker treats as no info
        // (every row fully opaque) instead of "all stopped" (every row faded).
        assertEquals(null, probe.runningOf(listOf(safari)))
    }

    @Test
    fun linuxMatchesWhenProcessArgvBasenameMatchesDesktopExecBasename() = runTest {
        // Linux .desktop file says `Exec=firefox %u`. The actual running
        // process exec'd from `/usr/lib/firefox/firefox-bin` but the
        // wrapper sets argv[0] back to `firefox`. Basename match wins.
        val desktop = tmp.newFile("firefox.desktop")
        desktop.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val firefox = Browser(
            bundleId = "firefox",
            displayName = "Firefox",
            applicationPath = desktop.absolutePath,
            version = null,
        )
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Linux,
            runCommand = { error("ps must not be invoked on Linux host") },
            procCmdlines = { listOf("firefox", "/usr/bin/zsh", "/usr/lib/systemd/systemd") },
            pathLookup = { name -> if (name == "firefox") "/usr/bin/firefox" else null },
        )
        assertEquals(setOf(firefox.toBrowserId()), probe.runningOf(listOf(firefox)))
    }

    @Test
    fun linuxMatchesWhenProcessExePathMatchesAbsoluteExec() = runTest {
        // .desktop with absolute path → snap binary running with the same
        // absolute path. Exact-match path wins.
        val desktop = tmp.newFile("chromium.desktop")
        desktop.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Chromium
            Exec=/snap/chromium/current/usr/lib/chromium-browser/chromium-browser %U
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val chromium = Browser(
            bundleId = "chromium",
            displayName = "Chromium",
            applicationPath = desktop.absolutePath,
            version = null,
        )
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Linux,
            runCommand = { error("ps must not be invoked") },
            procCmdlines = {
                listOf(
                    "/usr/bin/zsh",
                    "/snap/chromium/current/usr/lib/chromium-browser/chromium-browser",
                )
            },
            pathLookup = { error("PATH lookup not needed for absolute Exec") },
        )
        assertEquals(setOf(chromium.toBrowserId()), probe.runningOf(listOf(chromium)))
    }

    @Test
    fun linuxReturnsEmptyWhenBrowserNotRunning() = runTest {
        val desktop = tmp.newFile("firefox.desktop")
        desktop.writeText(
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val firefox = Browser(
            bundleId = "firefox",
            displayName = "Firefox",
            applicationPath = desktop.absolutePath,
            version = null,
        )
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Linux,
            runCommand = { error("ps must not be invoked") },
            procCmdlines = { listOf("/usr/bin/zsh", "/lib/systemd/systemd") },
            pathLookup = { name -> if (name == "firefox") "/usr/bin/firefox" else null },
        )
        assertEquals(emptySet(), probe.runningOf(listOf(firefox)))
    }

    @Test
    fun linuxReturnsEmptyWhenDesktopFileMissing() = runTest {
        val ghost = Browser(
            bundleId = "ghost",
            displayName = "Ghost",
            applicationPath = "/nonexistent/ghost.desktop",
            version = null,
        )
        val probe = JvmRunningBrowserProbe(
            hostOs = HostOs.Linux,
            runCommand = { error("ps must not be invoked") },
            procCmdlines = { listOf("ghost") },
            pathLookup = { error("not invoked") },
        )
        assertEquals(emptySet(), probe.runningOf(listOf(ghost)))
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
