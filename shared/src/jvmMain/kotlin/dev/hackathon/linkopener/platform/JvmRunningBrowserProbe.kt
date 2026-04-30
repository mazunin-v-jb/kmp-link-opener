package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.platform.linux.readDesktopEntry
import dev.hackathon.linkopener.platform.linux.stripExecFieldCodes
import java.io.File
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
    // Linux-specific injection points: enumerating /proc and resolving a
    // bare command name through $PATH. Kept as constructor params so unit
    // tests can avoid touching the real filesystem.
    private val procCmdlines: () -> List<String> = ::defaultProcCmdlines,
    private val pathLookup: (String) -> String? = ::defaultPathLookup,
) : RunningBrowserProbe {

    override suspend fun runningOf(installed: List<Browser>): Set<BrowserId>? =
        withContext(Dispatchers.IO) {
            // Hosts where we have no enumerator (Android / Other) get a null
            // upstream so the picker falls back to "no info" rather than
            // claiming everything is stopped.
            if (hostOs == HostOs.Android || hostOs == HostOs.Other) {
                return@withContext null
            }
            val running = enumerateRunningPaths()
            // Cache per-browser resolution once, before iterating processes
            // — `resolveBrowserPath` on Linux reads the .desktop file off
            // disk and we don't want to re-parse on every `any { … }` step.
            val browserPaths = installed.associateWith { resolveBrowserPath(it) }
            val matched = installed
                .filter { browser ->
                    val resolved = browserPaths[browser] ?: return@filter false
                    running.any { matchesBrowser(it, resolved) }
                }
                .map { it.toBrowserId() }
                .toSet()
            // One-line summary every call — picker invocations are rare so
            // this isn't noisy, and it gives us enough breadcrumb to tell
            // "probe never ran" from "probe ran but matched nothing" when
            // chasing a 'no icons tinted' report. The per-browser detail
            // is the meat for diagnosis: which path each .desktop resolved
            // to, which is where Linux quirks usually hide.
            println(
                "[probe] hostOs=$hostOs installed=${installed.size} " +
                    "running=${running.size} matched=${matched.size}",
            )
            browserPaths.forEach { (browser, resolved) ->
                val tag = if (browser.toBrowserId() in matched) "RUN" else "off"
                println("[probe]   $tag ${browser.displayName} -> $resolved")
            }
            // Sample of running cmdlines so a 'matched=0' report can be
            // diagnosed without a second roundtrip — usually it's "browser
            // wasn't actually running when picker opened" rather than a
            // matching bug, and seeing the cmdline list makes that
            // immediately obvious.
            val sample = running.filter { it.contains("firefox", ignoreCase = true) ||
                it.contains("chromium", ignoreCase = true) ||
                it.contains("chrome", ignoreCase = true) ||
                it.contains("brave", ignoreCase = true) }
            if (sample.isNotEmpty()) {
                println("[probe]   browser-like cmdlines: ${sample.take(8)}")
            } else {
                println("[probe]   no firefox/chromium/chrome/brave cmdline among $${running.size} processes")
            }
            matched
        }

    private fun enumerateRunningPaths(): List<String> = when (hostOs) {
        // `ps -ax -o comm=` lists every process's command path with no
        // header line — for `.app` bundles these look like
        // `/Applications/Safari.app/Contents/MacOS/Safari`, perfect for
        // prefix-matching against the bundle root.
        HostOs.MacOs -> runCommand(listOf("ps", "-ax", "-o", "comm="))
        // Linux: `ps -o comm=` truncates at 15 chars (kernel COMM limit)
        // and is just a basename, so it can't disambiguate. Read
        // `/proc/<pid>/cmdline` instead — that gives us argv[0] of every
        // process, which the wrapper-script cases (`/usr/bin/firefox` →
        // `firefox`) and absolute-path cases land on equally well.
        HostOs.Linux -> procCmdlines()
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

    /**
     * Per-OS conversion of a [Browser]'s `applicationPath` into the
     * "resolved path" we'll compare against running-process entries.
     *
     * - **macOS / Windows**: applicationPath IS the executable / bundle
     *   path, so just hand it back.
     * - **Linux**: applicationPath is a `.desktop` file path. We parse
     *   `Exec=`, take the first token (the binary), and resolve a bare
     *   name through `$PATH` so the comparison can be on equal footing
     *   with what `/proc/<pid>/cmdline` exposes.
     */
    private fun resolveBrowserPath(browser: Browser): String? = when (hostOs) {
        HostOs.MacOs, HostOs.Windows -> browser.applicationPath
        HostOs.Linux -> resolveLinuxBinary(browser.applicationPath)
        HostOs.Android, HostOs.Other -> null
    }

    private fun resolveLinuxBinary(desktopPath: String): String? {
        val entry = runCatching { readDesktopEntry(File(desktopPath)) }.getOrNull()
            ?: return null
        val raw = entry.string("Exec") ?: return null
        val first = stripExecFieldCodes(raw).firstOrNull() ?: return null
        return if (first.startsWith('/')) first else (pathLookup(first) ?: first)
    }

    private fun matchesBrowser(processPath: String, resolvedBrowserPath: String): Boolean =
        when (hostOs) {
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
            HostOs.MacOs -> processPath.startsWith("$resolvedBrowserPath/Contents/MacOS/")
            // Both sides are full exe paths; Windows paths are case-insensitive.
            HostOs.Windows -> processPath.equals(resolvedBrowserPath, ignoreCase = true)
            // On Linux argv[0] varies wildly: a `.desktop` says `Exec=firefox`
            // → resolves through PATH to `/usr/bin/firefox`, but the actual
            // running process exec'd to `/usr/lib/firefox/firefox-bin` with
            // argv[0] of just "firefox" (Mozilla's wrapper sets argv[0] back
            // to the friendly name). Matching is therefore "exact path or
            // matching basename" — both a snap binary at
            // `/snap/chromium/3344/bin/chromium` and the wrapper-set
            // `chromium-browser` argv[0] land cleanly under the basename
            // case.
            HostOs.Linux -> processPath == resolvedBrowserPath ||
                File(processPath).name == File(resolvedBrowserPath).name
            HostOs.Android, HostOs.Other -> false
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

/**
 * Reads `argv[0]` from every numbered `/proc/<pid>/cmdline` (the kernel
 * exposes the joined argv, NUL-separated). Skips kernel threads (their
 * cmdline is empty) and any PID we can't read because of a permission
 * race during teardown.
 */
private fun defaultProcCmdlines(): List<String> {
    val procDir = File("/proc")
    if (!procDir.isDirectory) return emptyList()
    val children = procDir.listFiles { f -> f.isDirectory && f.name.toIntOrNull() != null }
        ?: return emptyList()
    // `argv[0]` is everything up to the first NUL byte. `takeWhile` with
    // `Char.code != 0` is unambiguous in source (NUL char literals get
    // mangled by some tooling).
    return children.mapNotNull { dir ->
        val cmdline = File(dir, "cmdline")
        if (!cmdline.canRead()) return@mapNotNull null
        runCatching {
            cmdline.readText().substringBefore(' ').takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}

/** Resolves a bare command name (e.g. "firefox") through `$PATH`. */
private fun defaultPathLookup(name: String): String? {
    val pathEnv = System.getenv("PATH").orEmpty()
    return pathEnv.split(':').asSequence()
        .filter { it.isNotEmpty() }
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }
        ?.absolutePath
}
