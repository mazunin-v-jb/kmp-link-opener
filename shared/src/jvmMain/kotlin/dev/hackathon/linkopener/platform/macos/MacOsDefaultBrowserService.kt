package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MacOsDefaultBrowserService(
    private val osVersion: String = System.getProperty("os.version").orEmpty(),
) : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    // TODO: real Launch Services check (LSCopyDefaultHandlerForURLScheme via
    //  JNA) lands when the app actually registers itself as a browser
    //  candidate (stage 3). Until then this app cannot be the default,
    //  so a hardcoded false matches reality.
    override suspend fun isDefaultBrowser(): Boolean = false

    override suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            ProcessBuilder("open", settingsUrlForCurrentOs())
                .inheritIO()
                .start()
                .waitFor()
        }.isSuccess
    }

    private fun settingsUrlForCurrentOs(): String {
        val major = osVersion.substringBefore('.').toIntOrNull() ?: 0
        // macOS 13 (Ventura) renamed System Preferences to System Settings and
        // moved "Default web browser" from General into Desktop & Dock. The
        // legacy `com.apple.preference.general` URL still launches Settings on
        // newer versions but lands on Appearance, which is wrong. Pick the
        // right pane based on the host's macOS major version.
        return if (major >= 13) {
            "x-apple.systempreferences:com.apple.Desktop-Settings.extension"
        } else {
            "x-apple.systempreferences:com.apple.preference.general"
        }
    }
}
