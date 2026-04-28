package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the URL in the chosen browser. Two argv shapes:
 *
 * - **No profile / non-Chromium:** `open -a <Browser.app> -- <url>`. `--`
 *   separates the URL from `open`'s own flags so a URL containing `?` / `&`
 *   reaches the browser intact.
 * - **Chromium-family with profile:** `open -na <Browser.app> --args
 *   --profile-directory=<id> <url>`. The `-n` forces `open` to spawn a NEW
 *   Chromium process — without it, when Chrome is already running, `open`
 *   sends the URL via the macOS "Open URL" Apple Event (which doesn't carry
 *   our `--profile-directory` flag) and Chrome ends up switching to the
 *   requested profile but never opening the URL. With `-n` the freshly
 *   spawned Chrome sees both args together, routes correctly, and (if
 *   Chrome was already running) hands the work off via Chromium IPC and
 *   exits.
 *
 * ProcessBuilder treats each arg as a literal argv entry, so paths with
 * spaces don't need quoting.
 */
class MacOsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
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
        return if (profile != null && browser.family == BrowserFamily.Chromium) {
            listOf(
                "open", "-na", browser.applicationPath,
                "--args",
                "--profile-directory=${profile.id}",
                url,
            )
        } else {
            listOf("open", "-a", browser.applicationPath, "--", url)
        }
    }
}
