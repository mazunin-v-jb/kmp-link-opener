package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
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
