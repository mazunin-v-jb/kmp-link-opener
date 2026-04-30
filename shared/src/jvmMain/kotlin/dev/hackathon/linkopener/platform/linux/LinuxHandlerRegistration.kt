package dev.hackathon.linkopener.platform.linux

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Drops a freedesktop `.desktop` file under
 * `$XDG_DATA_HOME/applications` so the app appears in System Settings ->
 * Default Applications and `xdg-mime` knows how to find us.
 *
 * Modeled on Chrome's own Linux installer behavior. Notably, this does
 * **not** rebind `x-scheme-handler/http` / `https` to us at every
 * startup — Chrome doesn't either; the user-driven flow is "click 'Set
 * as default' / pick us in System Settings -> xdg-settings handles the
 * rebind". Silently rewriting the user's chosen default on every
 * launch is hostile, so the only thing we do at boot is make ourselves
 * *available* as a choice.
 *
 * After writing the entry we run `update-desktop-database` to refresh
 * the freedesktop MIME cache so the new file is visible to xdg-mime /
 * xdg-settings without waiting for a session restart. Mint Cinnamon
 * preinstalls `desktop-file-utils` (which provides
 * `update-desktop-database`); on more minimal distros we no-op when
 * the binary isn't on `$PATH`.
 *
 * Idempotent: rewriting the same body every startup is cheap and
 * self-heals partial state.
 */
class LinuxHandlerRegistration(
    private val applicationsDir: Path = defaultApplicationsDir(),
    private val launchTokensProvider: () -> List<String>? = LinuxLaunchCommand::current,
    private val updateDesktopDatabaseRunner: (Path) -> Unit = ::defaultUpdateDb,
) {

    /**
     * Writes / overwrites the `.desktop` file. Returns true when the
     * file ended up on disk; the database refresh is best-effort and
     * not part of the success signal.
     */
    suspend fun register(): Boolean = withContext(Dispatchers.IO) {
        val tokens = launchTokensProvider() ?: return@withContext false
        val execLine = "${LinuxLaunchCommand.quote(tokens)} %u"
        val written = runCatching {
            Files.createDirectories(applicationsDir)
            Files.writeString(desktopPath(), buildDesktopBody(execLine))
        }.isSuccess
        if (written) runCatching { updateDesktopDatabaseRunner(applicationsDir) }
        written
    }

    private fun desktopPath(): Path = applicationsDir.resolve(DESKTOP_FILE_NAME)

    companion object {
        const val DESKTOP_FILE_NAME: String = "link-opener.desktop"
        const val OWN_DESKTOP_ID: String = "link-opener"

        internal fun defaultApplicationsDir(): Path {
            val raw = System.getenv("XDG_DATA_HOME").orEmpty()
            val base = if (raw.isNotBlank()) {
                raw
            } else {
                val home = System.getProperty("user.home").orEmpty()
                "$home/.local/share"
            }
            return Paths.get(base, "applications")
        }

        // text/html is part of "default web browser" per xdg-settings:
        // `xdg-settings check default-web-browser` only reports yes when
        // x-scheme-handler/http, x-scheme-handler/https AND text/html
        // all point at us. Including it lets the user's choice register
        // cleanly even though we route HTML files the same way as URLs
        // (through the picker).
        internal fun buildDesktopBody(execLine: String): String =
            """[Desktop Entry]
Type=Application
Name=Link Opener
GenericName=Web Browser
Comment=Pick which browser opens each link
Exec=$execLine
Terminal=false
Categories=Network;WebBrowser;
MimeType=x-scheme-handler/http;x-scheme-handler/https;text/html;
StartupNotify=false
""".trimIndent()

        private fun defaultUpdateDb(applicationsDir: Path) {
            // Best-effort cache refresh. xdg-mime works without it, just
            // a tick slower on the first lookup; on hosts without
            // `desktop-file-utils` we silently skip.
            val binary = LinuxDefaultBrowserService.defaultLookupOnPath("update-desktop-database")
                ?: return
            runCatching {
                ProcessBuilder(binary.absolutePath, applicationsDir.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(5, TimeUnit.SECONDS)
            }
        }
    }
}
