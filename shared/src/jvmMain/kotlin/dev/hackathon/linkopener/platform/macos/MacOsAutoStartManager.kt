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

        private fun defaultExecutablePath(): String {
            val javaHome = Paths.get(System.getProperty("java.home").orEmpty())
            return resolvePackagedExecutable(javaHome)?.toString()
                // Dev mode (gradle :desktopApp:run) — `RunAtLoad` against a JDK
                // executable obviously won't relaunch the gradle worker, but
                // having *something* in the plist at least keeps the toggle
                // round-trip observable for testing.
                ?: javaHome.resolve("bin").resolve("java").toString()
        }

        /**
         * If [javaHome] points inside a jpackage-style macOS bundle
         * (`<name>.app/Contents/runtime/Contents/Home`), returns the bundle's
         * launcher binary at `Contents/MacOS/<name>`. Returns `null` when the
         * JVM is running outside a packaged app (dev / unit tests / a
         * non-`.app` JDK). Visible for testing.
         */
        internal fun resolvePackagedExecutable(javaHome: Path): Path? {
            val appBundle = javaHome.parent?.parent?.parent?.parent ?: return null
            if (appBundle.fileName?.toString()?.endsWith(".app") != true) return null
            val macosDir = appBundle.resolve("Contents").resolve("MacOS")
            return runCatching {
                Files.list(macosDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }.findFirst().orElse(null)
                }
            }.getOrNull()
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
