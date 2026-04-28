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
        // TODO: figure out the correct deep-link URL for macOS Sonoma+
        //  (Darwin 25 / macOS 26-ish). Neither
        //  `x-apple.systempreferences:com.apple.preference.general` nor
        //  `com.apple.Desktop-Settings.extension` lands on the actual
        //  "Default web browser" dropdown on the test machine — the legacy
        //  one redirects to Appearance, the Desktop-Settings one drops the
        //  user on Wallpaper / something unrelated.
        //
        //  Things to try:
        //   - probe `defaults read /System/Library/PreferencePanes/.../*.plist`
        //     to enumerate available pane bundle IDs on the host
        //   - try `com.apple.DesktopAndDock-Settings.extension` and other
        //     plausible Settings extension IDs
        //   - or shell out to `osascript -e 'tell app "System Settings" to
        //     reveal pane id "..."'` for a more structured way to navigate
        //   - last resort: open System Settings root and rely on the
        //     in-app instruction text to guide the user
        //
        //  For now we keep the version-aware fallback so the button at least
        //  opens *some* settings pane on each macOS generation, even if it's
        //  not the exact destination.
        return if (major >= 13) {
            "x-apple.systempreferences:com.apple.Desktop-Settings.extension"
        } else {
            "x-apple.systempreferences:com.apple.preference.general"
        }
    }
}
