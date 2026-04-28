package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MacOsDefaultBrowserService : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    // TODO: real Launch Services check (LSCopyDefaultHandlerForURLScheme via
    //  JNA) lands when the app actually registers itself as a browser
    //  candidate (stage 3). Until then this app cannot be the default,
    //  so a hardcoded false matches reality.
    override suspend fun isDefaultBrowser(): Boolean = false

    override suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        // Opens System Settings → General. On macOS Sonoma+ the "Default web
        // browser" dropdown lives there. On Ventura the same panel exists.
        // On Big Sur/Monterey this lands in the General preferences pane.
        runCatching {
            ProcessBuilder("open", "x-apple.systempreferences:com.apple.preference.general")
                .inheritIO()
                .start()
                .waitFor()
        }.isSuccess
    }
}
