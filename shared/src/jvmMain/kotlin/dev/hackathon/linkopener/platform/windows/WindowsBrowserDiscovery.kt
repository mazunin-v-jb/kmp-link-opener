package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.detectBrowserFamilyByDisplayName
import dev.hackathon.linkopener.platform.BrowserDiscovery

/**
 * Windows browser discovery via the `StartMenuInternet` registry
 * convention.
 *
 * Both HKLM (system-wide installations) and HKCU (per-user installations
 * for tools like browser portable / msi-per-user installs) get
 * enumerated. Same browser appearing in both — typical for Chrome —
 * gets deduplicated by exe path.
 *
 * Per-browser layout:
 * ```
 * HKLM\SOFTWARE\Clients\StartMenuInternet\Google Chrome
 *     (Default)            REG_SZ    Google Chrome
 *     \Capabilities
 *         ApplicationName  REG_SZ    Google Chrome
 *     \shell\open\command
 *         (Default)        REG_SZ    "C:\Program Files\Google\Chrome\Application\chrome.exe"
 * ```
 *
 * We extract:
 * - **bundleId** — the registry sub-key name (e.g. `Google Chrome`).
 *   Treated as Windows' analogue of macOS's CFBundleIdentifier:
 *   stable across re-installs, used to detect Chromium family for
 *   profile expansion in a follow-up.
 * - **displayName** — `Capabilities\ApplicationName` if present,
 *   otherwise the `(Default)` value of the parent key, otherwise the
 *   sub-key name itself.
 * - **applicationPath** — the path inside `shell\open\command`,
 *   stripped of surrounding quotes and any trailing `--single-argument
 *   "%1"` style argv suffix.
 * - **version** — null in this stage (PE version-info parsing lands
 *   in W7).
 *
 * Browsers without a `shell\open\command` are skipped — they have
 * nothing we can launch. Browsers whose exe path doesn't exist on
 * disk (broken uninstall remnants) get skipped at launch time, not
 * discovery time, since we don't read disk in this layer.
 */
class WindowsBrowserDiscovery(
    private val registry: RegistryReader = RegistryReader(),
) : BrowserDiscovery {

    override suspend fun discover(): List<Browser> {
        val machineKeys = enumerateBrowsers(HKLM_START_MENU_INTERNET)
        val userKeys = enumerateBrowsers(HKCU_START_MENU_INTERNET)
        // HKLM first so system-wide installs win on dedupe-by-path
        // collisions with portable per-user copies.
        return (machineKeys + userKeys)
            .distinctBy { it.applicationPath.lowercase() }
            .sortedBy { it.displayName.lowercase() }
    }

    private suspend fun enumerateBrowsers(parentPath: String): List<Browser> {
        val output = registry.query(parentPath) ?: return emptyList()
        val subKeys = RegistryReader.parseSubKeys(output, parentPath)
        return subKeys.mapNotNull { subKey -> readBrowser(parentPath, subKey) }
    }

    private suspend fun readBrowser(parentPath: String, subKey: String): Browser? {
        val basePath = "$parentPath\\$subKey"
        val commandPath = "$basePath\\shell\\open\\command"
        val rawCommand = registry.queryValue(commandPath) ?: return null
        val exePath = stripCommandSuffix(rawCommand) ?: return null

        val displayName =
            registry.queryValue("$basePath\\Capabilities", "ApplicationName")
                ?: registry.queryValue(basePath)
                ?: subKey

        // Windows doesn't have a stable bundle-id in the macOS sense.
        // Use the registry sub-key as our `bundleId` — a human-readable
        // but stable label across upgrades. Family detection by
        // keyword on the display name (the registry sub-key is also
        // typically a display name like "Google Chrome").
        val family = detectBrowserFamilyByDisplayName(displayName)

        return Browser(
            bundleId = subKey,
            displayName = displayName,
            applicationPath = exePath,
            version = null, // W7 — PE version-info parsing
            family = family,
        )
    }

    companion object {
        // Parent registry paths. HKLM is the canonical location for
        // system-wide browser installs; HKCU mirrors it for per-user
        // installs (Chrome's "Install for current user" and
        // single-binary portables).
        const val HKLM_START_MENU_INTERNET = "HKLM\\SOFTWARE\\Clients\\StartMenuInternet"
        const val HKCU_START_MENU_INTERNET = "HKCU\\SOFTWARE\\Clients\\StartMenuInternet"

        // The StartMenuInternet sub-key name we register under (matches
        // patch-msi-hklm.ps1 → SOFTWARE\Clients\StartMenuInternet\LinkOpener).
        // Used by DiscoverBrowsersUseCase to filter ourselves out of the list.
        const val OWN_START_MENU_KEY = "LinkOpener"

        /**
         * Strips quotes and any `--` / `-osint` / `%1` style argv
         * suffix that browsers tack onto their `shell\open\command`
         * registration.
         *
         * Examples:
         * - `"C:\Program Files\Google\Chrome\Application\chrome.exe"` →
         *   `C:\Program Files\Google\Chrome\Application\chrome.exe`
         * - `"C:\Program Files\Mozilla Firefox\firefox.exe" -osint -url "%1"` →
         *   same firefox.exe
         * - `C:\Apps\Edge\msedge.exe --single-argument %1` →
         *   `C:\Apps\Edge\msedge.exe`
         *
         * Returns null if the input has no recognisable .exe path.
         */
        fun stripCommandSuffix(rawCommand: String): String? {
            val trimmed = rawCommand.trim()
            // Quoted form: keep what's between the first quote and the
            // matching closing quote.
            if (trimmed.startsWith("\"")) {
                val close = trimmed.indexOf('"', startIndex = 1)
                if (close > 1) return trimmed.substring(1, close)
            }
            // Unquoted form: split on whitespace, keep leading tokens
            // until we've assembled a path that ends in `.exe`. Path
            // segments containing spaces (e.g. `C:\Program Files\…`)
            // require iterating; in practice unquoted commands are
            // rare on modern installers.
            val tokens = trimmed.split(' ')
            for (i in tokens.indices) {
                val candidate = tokens.subList(0, i + 1).joinToString(" ")
                if (candidate.endsWith(".exe", ignoreCase = true)) return candidate
            }
            return null
        }
    }
}
