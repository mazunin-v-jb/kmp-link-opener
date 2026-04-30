package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens a URL in the chosen browser by reading the browser's `.desktop`
 * file (`Browser.applicationPath`), extracting the binary from `Exec=`,
 * and spawning it with the URL on argv.
 *
 * Argv shapes — same family-driven dispatch as macOS / Windows:
 *
 * - **Chromium-family with profile:** `<binary> --profile-directory=<id> <url>`.
 *   Linux profile expansion isn't wired in this stage but the branch
 *   stays for forward compatibility with stage 08.1 work.
 * - **Firefox-family:** `<binary> <url>`. Mozilla's XPCOM remoting routes
 *   the URL to a running instance via D-Bus / mailbox files when the
 *   binary is invoked directly with a URL argv. No `open`-equivalent
 *   indirection.
 * - **Everyone else:** `<binary> <url>`. Most browsers accept the URL
 *   as the first non-flag argument.
 *
 * `Exec=` is parsed via [stripExecFieldCodes] so freedesktop placeholders
 * (`%u %U %f %F`) are dropped before we append our URL. If `Exec=`
 * couldn't be read or yielded an empty argv, we return false — there's
 * nothing meaningful to spawn.
 */
internal class LinuxLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
) : LinkLauncher {

    override suspend fun openIn(browser: Browser, url: String): Boolean =
        withContext(Dispatchers.IO) {
            val args = buildArgs(browser, url) ?: return@withContext false
            runCatching {
                val process = processFactory(args)
                // Wait so a non-zero exit (missing binary, bad argv) is
                // observable. The actual browser process detaches —
                // this just blocks on the launcher fork.
                process.waitFor() == 0
            }.getOrDefault(false)
        }

    private fun buildArgs(browser: Browser, url: String): List<String>? {
        val execTokens = readExecTokens(browser.applicationPath) ?: return null
        if (execTokens.isEmpty()) return null

        val profile = browser.profile
        val baseArgs = execTokens.toMutableList()
        if (profile != null && browser.family == BrowserFamily.Chromium) {
            baseArgs += "--profile-directory=${profile.id}"
        }
        baseArgs += url
        return baseArgs
    }

    private fun readExecTokens(desktopFilePath: String): List<String>? {
        val entry = runCatching { readDesktopEntry(File(desktopFilePath)) }.getOrNull()
            ?: return null
        val rawExec = entry.string("Exec") ?: return null
        return stripExecFieldCodes(rawExec)
    }
}
