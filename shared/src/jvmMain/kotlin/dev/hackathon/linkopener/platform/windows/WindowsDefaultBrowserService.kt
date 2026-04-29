package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads `HKCU\…\UserChoice\ProgId` to find out which app currently
 * handles HTTP/HTTPS links, and compares against our registered
 * ProgId. While the MSI installer doesn't yet register
 * [OWN_PROG_ID] (W5 in `ai_stages/07_windows_support/plan.md`), this
 * always reports "not default" — same observable behaviour as before
 * the read was wired, but with the right shape for when registration
 * lands.
 *
 * `openSystemSettings` deep-links to `ms-settings:defaultapps` —
 * Settings → Apps → Default apps. Per Microsoft's UX guidelines,
 * apps must NOT silently force the default-browser binding by
 * writing the registry directly (Windows 10+ will revert it on
 * launch); the user has to confirm in Settings.
 *
 * Live observation (`observeIsDefaultBrowser`) stays one-shot via
 * the interface default. The macOS impl uses a WatchService against
 * the LaunchServices plist; the Windows equivalent would be a
 * registry-watch via JNA, which we punt to a follow-up. The Settings
 * UI's manual refresh button covers the gap.
 */
class WindowsDefaultBrowserService(
    private val registry: RegistryReader = RegistryReader(),
) : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    override suspend fun isDefaultBrowser(): Boolean = withContext(Dispatchers.IO) {
        val httpProgId = registry.queryValue(USER_CHOICE_HTTP, "ProgId") ?: return@withContext false
        // Equality is the right comparison: ProgId values are atoms
        // (`ChromeHTML`, `MSEdgeHTM`, our `LinkOpener.URL`), case-
        // insensitive in Windows but reg.exe preserves casing.
        httpProgId.equals(OWN_PROG_ID, ignoreCase = true)
    }

    override suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        // ms-settings:defaultapps deep-links into Settings → Apps → Default apps.
        runCatching {
            ProcessBuilder("cmd", "/c", "start", "ms-settings:defaultapps")
                .inheritIO()
                .start()
                .waitFor()
        }.isSuccess
    }

    companion object {
        // The ProgId the MSI installer (stage W5) will register for our
        // app under HKLM\SOFTWARE\Classes\<this>. Must match whatever
        // the installer ultimately writes — kept here as a constant so
        // both the installer and the runtime read the same value.
        const val OWN_PROG_ID = "LinkOpener.URL"

        // HKCU\…\UserChoice is the per-user "which app handles http"
        // record set by the Settings → Default apps UI. We read the
        // `http` association; `https` is conventionally the same app
        // but we only check `http` since Windows 11's Settings flow
        // forces them to match.
        private const val USER_CHOICE_HTTP =
            "HKCU\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice"
    }
}
