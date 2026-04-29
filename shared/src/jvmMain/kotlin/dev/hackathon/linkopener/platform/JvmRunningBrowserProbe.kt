package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JVM impl: enumerates running processes once via a host-specific shell
 * command, then matches each [Browser]'s `applicationPath` against the
 * gathered list using OS-aware comparison rules.
 *
 * `runCommand` is injectable so tests can feed canned `ps` / PowerShell
 * output without spawning real processes.
 */
class JvmRunningBrowserProbe(
    private val hostOs: HostOs,
    private val runCommand: (List<String>) -> List<String> = ::defaultRunCommand,
) : RunningBrowserProbe {

    override suspend fun runningOf(installed: List<Browser>): Set<BrowserId> =
        withContext(Dispatchers.IO) {
            val running = enumerateRunningPaths()
            installed
                .filter { browser -> running.any { matchesBrowser(it, browser) } }
                .map { it.toBrowserId() }
                .toSet()
        }

    private fun enumerateRunningPaths(): List<String> = when (hostOs) {
        // `ps -ax -o comm=` lists every process's command path with no
        // header line — for `.app` bundles these look like
        // `/Applications/Safari.app/Contents/MacOS/Safari`, perfect for
        // prefix-matching against the bundle root.
        HostOs.MacOs, HostOs.Linux -> runCommand(listOf("ps", "-ax", "-o", "comm="))
        // PowerShell `Get-Process` exposes full executable paths via the
        // `Path` property (tasklist only gives the bare exe name, which
        // can't disambiguate parallel installs of the same browser at
        // different locations). `-NoProfile -NonInteractive` keeps
        // startup cost down and prevents prompt blocking.
        HostOs.Windows -> runCommand(
            listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "Get-Process | Where-Object { \$_.Path } | ForEach-Object { \$_.Path }",
            ),
        )
        HostOs.Android, HostOs.Other -> emptyList()
    }

    private fun matchesBrowser(processPath: String, browser: Browser): Boolean {
        val browserPath = browser.applicationPath
        return when (hostOs) {
            // Match only the bundle's own MAIN executable. macOS apps spawn
            // helpers in two subtrees we have to reject explicitly:
            // - `${bundle}/Contents/XPCServices/<svc>.xpc/Contents/MacOS/<svc>`:
            //   launchd-managed background agents (Safari's SafeBrowsing /
            //   History / BookmarksSyncAgent ones run even when Safari is
            //   closed). A naive `startsWith(bundle)` would mark Safari as
            //   running based purely on those — that's the bug we hit.
            // - `${bundle}/Contents/Frameworks/.../Helpers/...`: Chromium's
            //   renderer/GPU/utility helpers, only alive while the browser
            //   itself is running, but sometimes they linger briefly after
            //   shutdown.
            // The bundle's main exec lives directly at
            // `${bundle}/Contents/MacOS/<exec>`, so requiring that exact
            // prefix is enough to exclude both subtrees.
            HostOs.MacOs -> processPath.startsWith("$browserPath/Contents/MacOS/")
            // Both sides are full exe paths; Windows paths are case-insensitive.
            HostOs.Windows -> processPath.equals(browserPath, ignoreCase = true)
            // On Linux Stage 8 will hand us the launcher's resolved exe path;
            // until then this stays exact-match for forward-compat.
            HostOs.Linux -> processPath == browserPath
            HostOs.Android, HostOs.Other -> false
        }
    }
}

private fun defaultRunCommand(args: List<String>): List<String> {
    val process = ProcessBuilder(args).redirectErrorStream(true).start()
    val lines = process.inputStream.bufferedReader().useLines { it.toList() }
    // Bound the wait — ps and PowerShell normally complete in <500ms, but
    // we don't want a hung process to freeze the picker. Destroy if it
    // overshoots and we'll just return whatever lines we already drained.
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
    }
    return lines.filter { it.isNotBlank() }
}
