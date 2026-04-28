package dev.hackathon.linkopener.platform.macos

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import kotlin.io.path.Path
import kotlin.io.path.exists

class MacOsDefaultBrowserService(
    private val ownBundleId: String,
    private val osVersion: String = System.getProperty("os.version").orEmpty(),
    private val launchServicesPlist: Path = defaultLaunchServicesPlist(),
    private val plutilRunner: PlutilRunner = PlutilRunner(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DefaultBrowserService {

    override val canOpenSystemSettings: Boolean = true

    override suspend fun isDefaultBrowser(): Boolean = withContext(Dispatchers.IO) {
        if (!launchServicesPlist.exists()) return@withContext false
        val jsonText = plutilRunner.toJson(launchServicesPlist) ?: return@withContext false
        val handlers = runCatching {
            json.parseToJsonElement(jsonText).jsonObject["LSHandlers"] as? JsonArray
        }.getOrNull() ?: return@withContext false

        handlers.any { entry ->
            val obj = entry as? JsonObject ?: return@any false
            val scheme = obj["LSHandlerURLScheme"]?.jsonPrimitive?.contentOrNull
            val role = obj["LSHandlerRoleAll"]?.jsonPrimitive?.contentOrNull
            (scheme == "http" || scheme == "https") && role == ownBundleId
        }
    }

    override fun observeIsDefaultBrowser(): Flow<Boolean> = flow {
        emit(isDefaultBrowser())
        // Watch the parent directory: macOS rewrites the plist atomically (a
        // tmp file gets renamed onto the target), which surfaces as a CREATE
        // or MODIFY event on the parent rather than something we can register
        // on the file itself. ENTRY_DELETE catches the brief window during
        // an atomic rename.
        val parent = launchServicesPlist.parent
        if (parent == null || !Files.isDirectory(parent)) return@flow
        val watcher = parent.fileSystem.newWatchService()
        try {
            parent.register(
                watcher,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
            val targetName = launchServicesPlist.fileName.toString()
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
        // Filesystem events come in bursts (atomic-rename writes show up as
        // multiple events for a single user action), so dedupe consecutive
        // identical answers before they reach the UI.
        .distinctUntilChanged()
        // The watcher's blocking `take()` lives on the IO dispatcher; the
        // initial `isDefaultBrowser()` already does its own withContext(IO)
        // but flowOn keeps everything off the caller's dispatcher.
        .flowOn(Dispatchers.IO)

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
        //  For now we keep the version-aware fallback so the button at least
        //  opens *some* settings pane on each macOS generation, even if it's
        //  not the exact destination.
        return if (major >= 13) {
            "x-apple.systempreferences:com.apple.Desktop-Settings.extension"
        } else {
            "x-apple.systempreferences:com.apple.preference.general"
        }
    }

    private companion object {
        fun defaultLaunchServicesPlist(): Path = Path(
            System.getProperty("user.home").orEmpty(),
            "Library",
            "Preferences",
            "com.apple.LaunchServices",
            "com.apple.launchservices.secure.plist",
        )
    }
}
