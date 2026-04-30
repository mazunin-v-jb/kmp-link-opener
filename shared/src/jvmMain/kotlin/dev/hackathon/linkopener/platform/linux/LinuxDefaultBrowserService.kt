package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * Linux default-browser tracking modeled on Chromium's
 * `shell_integration_linux.cc`.
 *
 * Read path — primary: `xdg-settings check default-web-browser
 * <our.desktop>` returns `"yes\n"` / `"no\n"`. Same probe Chromium
 * uses; xdg-settings checks all of `x-scheme-handler/http`,
 * `x-scheme-handler/https` AND `text/html` so a yes answer means we
 * really are the user's web browser, not just a partial URL handler.
 *
 * Read path — fallback: if `xdg-settings` is missing, scan
 * `~/.config/mimeapps.list`'s `[Default Applications]` for
 * `x-scheme-handler/http=`. xdg-utils is preinstalled on Mint /
 * Ubuntu / Fedora / Arch / openSUSE so this is rarely exercised, but
 * it keeps us honest on minimal containers.
 *
 * Observe path: WatchService over the parent of `mimeapps.list`. Most
 * settings panels and `xdg-mime` rewrite this file when the user
 * changes the default; the WatchService matches macOS's identical
 * pattern over the LaunchServices plist.
 *
 * Open settings path: DE-aware dispatch keyed off `XDG_CURRENT_DESKTOP`.
 * Cinnamon, GNOME, KDE, XFCE, MATE all expose a "Default Applications"
 * dialog through a binary in `$PATH`; we probe each candidate's
 * presence before claiming `canOpenSystemSettings = true`.
 */
internal class LinuxDefaultBrowserService(
    private val ownDesktopId: String = DEFAULT_OWN_DESKTOP_ID,
    private val mimeAppsList: Path = defaultMimeAppsList(),
    private val xdgCurrentDesktop: () -> String =
        { System.getenv("XDG_CURRENT_DESKTOP").orEmpty() },
    private val pathLookup: (String) -> File? = ::defaultLookupOnPath,
    private val processRunner: (List<String>) -> ProcessOutput = ::defaultRun,
    private val processSpawner: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
) : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean by lazy { resolveSettingsCommand() != null }

    override suspend fun isDefaultBrowser(): Boolean = withContext(Dispatchers.IO) {
        checkViaXdgSettings() ?: checkViaMimeAppsList()
    }

    override fun observeIsDefaultBrowser(): Flow<Boolean> = flow {
        emit(isDefaultBrowser())
        val parent = mimeAppsList.parent
        if (parent == null || !Files.isDirectory(parent)) return@flow
        val watcher = parent.fileSystem.newWatchService()
        try {
            parent.register(
                watcher,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            val targetName = mimeAppsList.fileName.toString()
            while (currentCoroutineContext().isActive) {
                val key = try {
                    watcher.take()
                } catch (_: ClosedWatchServiceException) {
                    break
                } catch (_: InterruptedException) {
                    break
                }
                val touched = key.pollEvents().any { event ->
                    val ctx = event.context() as? Path ?: return@any false
                    ctx.fileName.toString() == targetName
                }
                if (touched) emit(isDefaultBrowser())
                if (!key.reset()) break
            }
        } finally {
            withContext(NonCancellable) { runCatching { watcher.close() } }
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override suspend fun openSystemSettings(): Boolean = withContext(Dispatchers.IO) {
        val command = resolveSettingsCommand() ?: return@withContext false
        runCatching {
            val process = processSpawner(command)
            // Settings panels keep running. Just probe that the spawn
            // itself succeeded; a failed exec throws.
            process.isAlive || process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun checkViaXdgSettings(): Boolean? {
        if (pathLookup("xdg-settings") == null) return null
        val output = runCatching {
            processRunner(listOf("xdg-settings", "check", "default-web-browser", "$ownDesktopId.desktop"))
        }.getOrNull() ?: return null
        if (output.exitCode != 0) return null
        // xdg-settings prints "yes" or "no" terminated by a newline.
        return output.stdout.trim() == "yes"
    }

    private fun checkViaMimeAppsList(): Boolean {
        val file = mimeAppsList.toFile()
        if (!file.isFile) return false
        var inDefaults = false
        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.startsWith("[")) {
                    inDefaults = line == "[Default Applications]"
                    continue
                }
                if (!inDefaults) continue
                if (!line.startsWith("x-scheme-handler/http=")) continue
                // The value can be a list of fallback handlers separated
                // by `;`. The first non-empty entry wins.
                val raw = line.substringAfter('=').trim()
                val first = raw.split(';').firstOrNull { it.isNotEmpty() } ?: return false
                return first == "$ownDesktopId.desktop"
            }
        }
        return false
    }

    private fun resolveSettingsCommand(): List<String>? {
        val desktop = xdgCurrentDesktop().lowercase()
        for ((token, command) in DESKTOP_SETTINGS_COMMANDS) {
            if (token in desktop && pathLookup(command.first()) != null) {
                return command
            }
        }
        return null
    }

    data class ProcessOutput(val exitCode: Int, val stdout: String)

    companion object {
        const val DEFAULT_OWN_DESKTOP_ID: String = "link-opener"

        // DE-token -> argv list. Token is matched case-insensitively
        // against XDG_CURRENT_DESKTOP (which on Mint Cinnamon reads
        // "X-Cinnamon", on KDE Plasma "KDE", etc.). We probe each
        // command on $PATH before claiming we can open settings.
        private val DESKTOP_SETTINGS_COMMANDS: List<Pair<String, List<String>>> = listOf(
            "cinnamon" to listOf("cinnamon-settings", "default"),
            "gnome" to listOf("gnome-control-center", "default-applications"),
            "unity" to listOf("gnome-control-center", "default-applications"),
            "kde" to listOf("kcmshell6", "kcm_componentchooser"),
            "xfce" to listOf("exo-preferred-applications"),
            "mate" to listOf("mate-default-applications-properties"),
        )

        internal fun defaultMimeAppsList(): Path {
            val configHome = System.getenv("XDG_CONFIG_HOME").orEmpty()
                .ifBlank {
                    val home = System.getProperty("user.home").orEmpty()
                    "$home/.config"
                }
            return Paths.get(configHome, "mimeapps.list")
        }

        internal fun defaultLookupOnPath(name: String): File? {
            val pathEnv = System.getenv("PATH").orEmpty()
            return pathEnv.split(':').asSequence()
                .filter { it.isNotEmpty() }
                .map { File(it, name) }
                .firstOrNull { it.canExecute() }
        }

        internal fun defaultRun(args: List<String>): ProcessOutput {
            val process = ProcessBuilder(args).redirectErrorStream(false).start()
            val out = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ProcessOutput(exitCode = -1, stdout = out)
            }
            return ProcessOutput(exitCode = process.exitValue(), stdout = out)
        }
    }
}
