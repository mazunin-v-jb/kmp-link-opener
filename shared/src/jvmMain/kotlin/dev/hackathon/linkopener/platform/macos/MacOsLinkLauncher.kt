package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.platform.LinkLauncher
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the URL in the chosen browser. Three argv shapes, picked by family:
 *
 * - **Chromium-family with profile:** `open -na <Browser.app> --args
 *   --profile-directory=<id> <url>`. The `-n` forces `open` to spawn a NEW
 *   Chromium process — without it, when Chrome is already running, `open`
 *   sends the URL via the macOS "Open URL" Apple Event (which doesn't carry
 *   our `--profile-directory` flag) and Chrome ends up switching to the
 *   requested profile but never opening the URL. With `-n` the freshly
 *   spawned Chrome sees both args together, routes correctly, and (if
 *   Chrome was already running) hands the work off via Chromium IPC and
 *   exits.
 * - **Firefox-family (any Gecko fork):** invoke the bundle's main binary
 *   directly — `<bundle>/Contents/MacOS/<CFBundleExecutable> <url>`. Mozilla
 *   bug 531552 / 1987335: `open -a Firefox <url>` cold-starts Firefox but
 *   the URL gets dropped in a startup race against Apple Events. Mozilla's
 *   own remoting (binary + URL on argv) handles BOTH states reliably — cold
 *   start spawns Firefox with URL on argv; if Firefox is already running,
 *   the second invocation forwards URL to the running instance via XPCOM
 *   IPC and exits. CFBundleExecutable is read from Info.plist so this
 *   generalises across Tor / Waterfox / LibreWolf / Mullvad / etc.
 * - **Everyone else:** `open -a <Browser.app> -- <url>`. `--` separates the
 *   URL from `open`'s own flags so a URL with `?` / `&` reaches the browser
 *   intact.
 *
 * ProcessBuilder treats each arg as a literal argv entry, so paths with
 * spaces don't need quoting.
 */
class MacOsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
    // Reads `CFBundleExecutable` from `${bundlePath}/Contents/Info.plist`.
    // Returns null if plutil failed / field absent — the launcher then
    // falls back to `open -a`. Pulled into a constructor param so tests
    // exercise the Firefox path without invoking real plutil.
    private val bundleExecutableNameReader: (String) -> String? = ::defaultReadBundleExecutableName,
) : LinkLauncher {

    override suspend fun openIn(browser: Browser, url: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val args = buildArgs(browser, url)
                val process = processFactory(args)
                // `open` returns quickly once Launch Services has accepted the
                // request; the actual browser process is spawned independently.
                // We still waitFor() so a misconfigured path / non-zero exit is
                // observable here.
                process.waitFor() == 0
            }.getOrDefault(false)
        }

    private fun buildArgs(browser: Browser, url: String): List<String> {
        val profile = browser.profile
        if (profile != null && browser.family == BrowserFamily.Chromium) {
            return listOf(
                "open", "-na", browser.applicationPath,
                "--args",
                "--profile-directory=${profile.id}",
                url,
            )
        }
        if (browser.family == BrowserFamily.Firefox) {
            val execName = bundleExecutableNameReader(browser.applicationPath)
            if (execName != null) {
                return listOf("${browser.applicationPath}/Contents/MacOS/$execName", url)
            }
            // Couldn't read CFBundleExecutable — fall through to `open -a`.
            // Warm Firefox still routes the URL correctly; cold start may
            // drop it (Mozilla bug 531552). Better than silent failure.
        }
        return listOf("open", "-a", browser.applicationPath, "--", url)
    }
}

private fun defaultReadBundleExecutableName(bundlePath: String): String? {
    val plistPath = "$bundlePath/Contents/Info.plist"
    return runCatching {
        val process = ProcessBuilder(
            "/usr/bin/plutil", "-extract", "CFBundleExecutable", "raw", "-o", "-", plistPath,
        ).redirectErrorStream(false).start()
        val out = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        if (process.exitValue() != 0) null else out.takeIf { it.isNotBlank() }
    }.getOrNull()
}
