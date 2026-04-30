package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.detectBrowserFamilyByDisplayName
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers installed browsers by walking the standard XDG application
 * directories and filtering `.desktop` entries that declare themselves
 * as URL handlers (`MimeType=` containing `x-scheme-handler/http` or
 * `x-scheme-handler/https`).
 *
 * `applicationPath` on Linux is the absolute path of the `.desktop`
 * file (consistent with how [LinuxBrowserIconLoader] consumes it).
 * `bundleId` is the `.desktop` basename without extension — what
 * `xdg-mime default <id> x-scheme-handler/http` takes, and stable
 * across upgrades.
 */
internal class LinuxBrowserDiscovery(
    private val xdgDataDirs: () -> List<String> = ::defaultXdgDataDirs,
    private val xdgDataHome: () -> String = ::defaultXdgDataHome,
) : BrowserDiscovery {

    override suspend fun discover(): List<Browser> = withContext(Dispatchers.IO) {
        val dirs = candidateDirs()
        // Dedupe by .desktop application ID (basename without extension).
        // freedesktop's ID-based lookup says: when the same `<id>.desktop`
        // appears in multiple data dirs, the higher-priority dir wins.
        // We walk $XDG_DATA_HOME before $XDG_DATA_DIRS so user-dropped
        // overrides shadow system-wide entries.
        val byBundleId = mutableMapOf<String, Browser>()
        for (dir in dirs) {
            val files = listDesktopFiles(dir) ?: continue
            for (file in files) {
                val browser = parseAsBrowser(file) ?: continue
                byBundleId.putIfAbsent(browser.bundleId, browser)
            }
        }
        byBundleId.values.sortedBy { it.displayName.lowercase() }
    }

    private fun candidateDirs(): List<File> {
        val ordered = mutableListOf<String>()
        ordered += xdgDataHome()
        ordered += xdgDataDirs()
        return ordered.flatMap { dataDir ->
            listOf(
                File(dataDir, "applications"),
                File(dataDir, "flatpak/exports/share/applications"),
            )
        }.distinctBy { it.absolutePath }
    }

    private fun listDesktopFiles(dir: File): List<File>? {
        if (!dir.isDirectory) return null
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".desktop") }?.toList()
    }

    private fun parseAsBrowser(file: File): Browser? {
        val entry = readDesktopEntry(file) ?: return null
        if (entry.string("Type") != "Application") return null
        if (entry.boolean("Hidden") || entry.boolean("NoDisplay")) return null
        val mimeTypes = entry.semicolonList("MimeType")
        val isLinkHandler = mimeTypes.any { it == HTTP_SCHEME || it == HTTPS_SCHEME }
        if (!isLinkHandler) return null
        // Use the bare `Name=` value rather than the locale-suffixed
        // variant. `Name=` is canonically the brand name (typically
        // English) on every browser package we've seen — Mozilla, Google,
        // Brave, Microsoft, etc. all keep it stable across locales. The
        // Name[ru] / Name[de] variants vary in completeness, can be
        // translated by community packagers, and don't always match the
        // user's app-language choice (the JVM's default Locale on Linux
        // depends on env vars that aren't always aligned with our
        // AppLanguage). Matching what most cross-platform browser pickers
        // do is simpler and predictable.
        val displayName = entry.string("Name") ?: return null
        // Exec= is required by spec for Type=Application; reject the
        // entry early if missing so the launcher doesn't deal with
        // null applicationPath later.
        if (entry.string("Exec").isNullOrBlank()) return null

        val canonicalPath = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val bundleId = file.name.substringBeforeLast(".desktop")
        return Browser(
            bundleId = bundleId,
            displayName = displayName,
            applicationPath = canonicalPath,
            version = null,
            family = detectBrowserFamilyByDisplayName(displayName),
        )
    }

    companion object {
        const val HTTP_SCHEME: String = "x-scheme-handler/http"
        const val HTTPS_SCHEME: String = "x-scheme-handler/https"

        // Freedesktop fallback: if XDG_DATA_HOME is unset, use ~/.local/share.
        internal fun defaultXdgDataHome(): String {
            val raw = System.getenv("XDG_DATA_HOME")
            if (!raw.isNullOrBlank()) return raw
            val home = System.getProperty("user.home").orEmpty()
            return "$home/.local/share"
        }

        // Freedesktop fallback: if XDG_DATA_DIRS is unset, use
        // /usr/local/share:/usr/share. Snap and flatpak system-wide
        // exports also live here on Mint.
        internal fun defaultXdgDataDirs(): List<String> {
            val raw = System.getenv("XDG_DATA_DIRS")
            val base = if (raw.isNullOrBlank()) {
                listOf("/usr/local/share", "/usr/share")
            } else {
                raw.split(':').filter { it.isNotEmpty() }
            }
            // Snap exposes installed apps under this prefix; not in
            // XDG_DATA_DIRS by default but Mint Cinnamon lists snap
            // browsers from there in the application menu.
            return base + listOf("/var/lib/snapd/desktop")
        }
    }
}
