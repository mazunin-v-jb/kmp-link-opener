package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Uses `AssocQueryStringW` (Shlwapi) to find out which app currently
 * handles HTTP/HTTPS links, and compares against our registered ProgId.
 * This is the same API Windows itself uses when resolving clicked links,
 * so it reflects the true effective default — unlike reading UserChoice
 * directly, it isn't fooled by Chrome's elevation service resetting the
 * raw registry key.
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
    private val progIdResolver: (protocol: String) -> String? = ::queryProtocolProgId,
) : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    override suspend fun isDefaultBrowser(): Boolean = withContext(Dispatchers.IO) {
        val progId = progIdResolver("http") ?: return@withContext false
        // AssocQueryStringW can return either value depending on how Windows
        // resolves the association:
        //  - OWN_PROG_ID ("LinkOpener.URL") when UserChoice is intact and
        //    points to our registered ProgId class.
        //  - OWN_START_MENU_KEY ("LinkOpener") when Chrome's elevation service
        //    has reset UserChoice and Windows falls back to resolving via
        //    the StartMenuInternet sub-key (our HKCU/HKLM Capabilities entry).
        progId.equals(OWN_PROG_ID, ignoreCase = true) ||
            progId.equals(WindowsBrowserDiscovery.OWN_START_MENU_KEY, ignoreCase = true)
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
        // The ProgId registered in HKCU/HKLM\SOFTWARE\Classes\<this> and in
        // URLAssociations. When UserChoice is intact this is what AssocQueryStringW
        // returns; used by WindowsHandlerRegistration to name the ProgId class.
        const val OWN_PROG_ID = "LinkOpener.URL"
    }
}
