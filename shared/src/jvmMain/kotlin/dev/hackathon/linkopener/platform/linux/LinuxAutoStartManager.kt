package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.platform.AutoStartManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Linux session autostart via the freedesktop XDG Autostart spec: any
 * `.desktop` file in `$XDG_CONFIG_HOME/autostart` (default
 * `~/.config/autostart`) gets launched on session start. We use the
 * user-scope location so no elevation is needed.
 *
 * The generated entry uses the same launch tokens helper as
 * [LinuxHandlerRegistration] so the two artefacts agree on whether the
 * app is running as a fat-JAR (`java -jar …`) or a packaged binary.
 *
 * On disable, we just delete the file; on Cinnamon's "Startup
 * Applications" panel that surfaces immediately as the entry
 * disappearing.
 */
internal class LinuxAutoStartManager(
    private val autostartDir: Path = defaultAutostartDir(),
    private val launchTokensProvider: () -> List<String>? = LinuxLaunchCommand::current,
    private val fileName: String = DEFAULT_FILE_NAME,
) : AutoStartManager {

    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        Files.exists(desktopPath())
    }

    override suspend fun setEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            if (enabled) write() else delete()
        }
    }

    private fun desktopPath(): Path = autostartDir.resolve(fileName)

    private fun write() {
        Files.createDirectories(autostartDir)
        val tokens = launchTokensProvider() ?: return
        val execLine = LinuxLaunchCommand.quote(tokens)
        Files.writeString(desktopPath(), buildDesktopBody(execLine))
    }

    private fun delete() {
        Files.deleteIfExists(desktopPath())
    }

    companion object {
        const val DEFAULT_FILE_NAME: String = "link-opener.desktop"

        internal fun defaultAutostartDir(): Path {
            val configHome = System.getenv("XDG_CONFIG_HOME").orEmpty()
                .ifBlank {
                    val home = System.getProperty("user.home").orEmpty()
                    "$home/.config"
                }
            return Paths.get(configHome, "autostart")
        }

        internal fun buildDesktopBody(execLine: String): String =
            """[Desktop Entry]
Type=Application
Name=Link Opener
Exec=$execLine
X-GNOME-Autostart-enabled=true
Hidden=false
NoDisplay=false
""".trimIndent()
    }
}
