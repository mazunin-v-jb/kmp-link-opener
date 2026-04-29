package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.platform.BrowserIconLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a browser's icon by parsing its `.desktop` file and walking the
 * XDG icon-theme search path. Implements the freedesktop Icon Theme Spec to
 * the level our use-case needs: read `Icon=…` from the desktop entry, then
 * (a) treat it as an absolute path if it points at one, or (b) probe the
 * standard search dirs for a `<name>.png` / `.svg` / `.xpm`.
 *
 * We don't honor full theme inheritance (parent themes, contexts, sizes) —
 * the picker rows are tiny and any reasonably-sized icon at any reasonable
 * resolution looks fine. If the only hit is `48x48` and the user wanted
 * `256x256`, Compose downscales perfectly. The shortcut keeps this loader
 * dependency-free — no third-party theme parser, no D-Bus.
 *
 * `applicationPath` is expected to point at the browser's `.desktop` file
 * (e.g. `/usr/share/applications/firefox.desktop`) — that's what the
 * forthcoming `LinuxBrowserDiscovery` (stage 8) will populate.
 */
internal class LinuxBrowserIconLoader(
    private val xdgDataDirs: List<String> = defaultXdgDataDirs(),
    private val homeIconsDir: String = System.getProperty("user.home")
        ?.let { "$it/.icons" }
        ?: "",
) : BrowserIconLoader {

    override suspend fun load(applicationPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val desktopFile = File(applicationPath)
        if (!desktopFile.exists() || !desktopFile.isFile) return@withContext null

        val iconValue = readIconKey(desktopFile) ?: return@withContext null

        // Absolute path: the spec allows `Icon=/full/path/to/foo.png` and lots
        // of vendor packages do exactly that. Trust it.
        if (iconValue.startsWith('/')) {
            val direct = File(iconValue)
            return@withContext if (direct.exists()) direct.readBytes() else null
        }

        // Otherwise look up the icon name in the XDG search dirs. PNG first
        // (rasterized icon themes outnumber SVG ones), then SVG, then XPM as
        // the last-resort fallback for ancient distros.
        for (extension in SUPPORTED_EXTENSIONS) {
            val match = findIconFile(iconValue, extension) ?: continue
            return@withContext match.readBytes()
        }
        null
    }

    private fun readIconKey(desktopFile: File): String? {
        // .desktop is a small INI; we only want the `[Desktop Entry]` group's
        // `Icon=` value. Scan top-down, stop on the first `Icon=` after the
        // group header to avoid grabbing values from `[Desktop Action …]`
        // sub-groups (which can also carry `Icon=`).
        var inMainGroup = false
        desktopFile.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inMainGroup = line == "[Desktop Entry]"
                    continue
                }
                if (!inMainGroup) continue
                if (line.startsWith("Icon=")) {
                    return line.substringAfter("Icon=").trim().takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }

    private fun findIconFile(name: String, extension: String): File? {
        // Order: $HOME/.icons → $XDG_DATA_DIRS/icons/<theme> → /usr/share/pixmaps
        // (matching the spec's lookup order). We don't pin a specific theme;
        // a depth-3 directory walk is enough to find the icon in any
        // size/context bucket the distro ships.
        val roots = buildList {
            if (homeIconsDir.isNotEmpty()) add(File(homeIconsDir))
            for (dir in xdgDataDirs) add(File(dir, "icons"))
            add(File("/usr/share/pixmaps"))
        }

        for (root in roots) {
            val match = walkForIcon(root, name, extension) ?: continue
            return match
        }
        return null
    }

    private fun walkForIcon(root: File, name: String, extension: String): File? {
        if (!root.isDirectory) return null
        // The deepest layout we expect is `<root>/<theme>/<size>/<context>/<name>.<ext>`,
        // e.g. `/usr/share/icons/hicolor/48x48/apps/firefox.png` — depth 4
        // from the search root. The flattest is `/usr/share/pixmaps/firefox.png`
        // — depth 1. `Files.walk` covers both with a max-depth cap so we
        // don't recurse into pathological themes that nest indefinitely.
        val target = "$name.$extension"
        return runCatching {
            Files.walk(root.toPath(), MAX_WALK_DEPTH).use { stream ->
                stream
                    .filter { path: Path ->
                        Files.isRegularFile(path) && path.fileName?.toString() == target
                    }
                    .findFirst()
                    .orElse(null)
                    ?.toFile()
            }
        }.getOrNull()
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = listOf("png", "svg", "xpm")
        // Covers `<theme>/<size>/<context>/<file>` (depth 4) with one slot of
        // headroom for distros that add an extra grouping layer.
        private const val MAX_WALK_DEPTH: Int = 5

        // freedesktop's "if XDG_DATA_DIRS is unset, fall back to
        // /usr/local/share:/usr/share". Mirrored here so unit tests can pass
        // a hand-crafted list without poking process env vars.
        internal fun defaultXdgDataDirs(): List<String> {
            val raw = System.getenv("XDG_DATA_DIRS")
            return if (raw.isNullOrBlank()) {
                listOf("/usr/local/share", "/usr/share")
            } else {
                raw.split(':').filter { it.isNotEmpty() }
            }
        }
    }
}
