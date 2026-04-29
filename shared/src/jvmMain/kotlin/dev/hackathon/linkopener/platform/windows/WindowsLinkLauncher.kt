package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the URL in the chosen browser by spawning the browser exe with
 * the URL as its first argv. Mirrors the macOS [MacOsLinkLauncher]
 * shape but without the macOS-specific `open -na` / Apple-Event glue —
 * Windows browsers accept URL argv directly without the multi-process
 * dance Chrome on macOS requires for `--profile-directory` to take
 * effect.
 *
 * Argv shapes:
 * - **No profile / non-Chromium:** `<exe> <url>`. Most browsers treat
 *   the first non-flag argument as a URL. Firefox accepts this too;
 *   Edge / Chrome / Brave / Opera all do.
 * - **Chromium-family with profile:** `<exe> --profile-directory=<id> <url>`.
 *   The profile flag must come before the URL on Windows; running
 *   Chromium picks up the request and routes the URL to that profile.
 *
 * No special handling for "Chrome already running" is needed on Windows.
 * Chromium browsers accept the new argv via their command-line IPC
 * (a named pipe in the user-data dir); the freshly spawned process
 * detects the existing instance and forwards the URL+flag, then exits.
 */
class WindowsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
) : LinkLauncher {

    override suspend fun openIn(browser: Browser, url: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val args = buildArgs(browser, url)
                val process = processFactory(args)
                // Browser process detaches quickly — `waitFor()` blocks
                // only on the brief launcher fork, not on the browser
                // itself.
                process.waitFor() == 0
            }.getOrDefault(false)
        }

    private fun buildArgs(browser: Browser, url: String): List<String> {
        val profile = browser.profile
        return if (profile != null && browser.family == BrowserFamily.Chromium) {
            listOf(
                browser.applicationPath,
                "--profile-directory=${profile.id}",
                url,
            )
        } else {
            listOf(browser.applicationPath, url)
        }
    }
}
