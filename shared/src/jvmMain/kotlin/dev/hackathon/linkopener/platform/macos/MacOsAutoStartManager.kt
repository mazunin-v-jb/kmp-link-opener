package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.platform.AutoStartManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MacOsAutoStartManager(
    private val launchAgentDir: Path = defaultLaunchAgentDir(),
    private val label: String = DEFAULT_LABEL,
    private val executableLocator: () -> String = ::defaultExecutablePath,
) : AutoStartManager {

    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        Files.exists(plistPath())
    }

    override suspend fun setEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            if (enabled) writePlist() else deletePlist()
        }
    }

    private fun plistPath(): Path = launchAgentDir.resolve("$label.plist")

    private fun writePlist() {
        Files.createDirectories(launchAgentDir)
        Files.writeString(plistPath(), buildPlistXml(label, executableLocator()))
    }

    private fun deletePlist() {
        Files.deleteIfExists(plistPath())
    }

    companion object {
        const val DEFAULT_LABEL: String = "dev.hackathon.linkopener"

        private fun defaultLaunchAgentDir(): Path =
            Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents")

        // TODO: when running from a packaged .app bundle, replace this with the
        //  bundle's MacOS executable path. The current best-effort path works
        //  in dev (gradle :desktopApp:run) but won't auto-start a real app
        //  until packaging lands. Tracked under stage 4 acceptance notes.
        private fun defaultExecutablePath(): String {
            val javaHome = System.getProperty("java.home")
            return Paths.get(javaHome, "bin", "java").toString()
        }

        internal fun buildPlistXml(label: String, executable: String): String =
            """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$label</string>
    <key>ProgramArguments</key>
    <array>
        <string>$executable</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
""".trimIndent()
    }
}
