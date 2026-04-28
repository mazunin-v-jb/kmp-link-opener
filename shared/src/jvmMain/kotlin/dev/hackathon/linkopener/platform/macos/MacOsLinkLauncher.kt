package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens the URL in the chosen browser via `open -a <Browser.app> -- <url>`.
 *
 * `--` separates the URL from `open`'s own flags so a URL containing things
 * like `?` or `&` reaches the browser intact. ProcessBuilder treats each
 * arg as a literal argv entry, so paths with spaces don't need quoting.
 */
class MacOsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
) : LinkLauncher {

    override suspend fun openIn(browser: Browser, url: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val process = processFactory(
                    listOf("open", "-a", browser.applicationPath, "--", url),
                )
                // `open` returns quickly once Launch Services has accepted the
                // request; the actual browser process is spawned independently.
                // We still waitFor() so a misconfigured path / non-zero exit is
                // observable here.
                process.waitFor() == 0
            }.getOrDefault(false)
        }
}
