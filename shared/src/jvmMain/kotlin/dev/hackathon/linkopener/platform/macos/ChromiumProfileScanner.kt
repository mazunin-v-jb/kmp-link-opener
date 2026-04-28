package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.BrowserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Reads `<userDataDir>/Local State` for Chromium-family browsers and returns
 * the list of profiles registered there. Each profile becomes its own
 * `Browser` row downstream (see `MacOsBrowserDiscovery`) so the user can
 * exclude / order / rule individual profiles.
 *
 * Resilient: missing file, malformed JSON, missing `profile.info_cache` —
 * all surface as `emptyList()` rather than throwing. The caller (discovery)
 * treats "no profiles found" the same as "browser has only the implicit
 * Default profile" → emit a single non-profile row.
 */
open class ChromiumProfileScanner(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    open fun scan(userDataDir: Path): List<BrowserProfile> {
        val localState = userDataDir.resolve("Local State")
        if (!localState.exists()) return emptyList()
        val text = runCatching { localState.readText() }.getOrNull() ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return emptyList()

        val infoCache = (root["profile"] as? JsonObject)
            ?.get("info_cache") as? JsonObject
            ?: return emptyList()

        // Stable order: "Default" first (Chromium's primary profile convention),
        // then everything else lexicographically.
        return infoCache.entries
            .map { (id, entry) ->
                val obj = entry as? JsonObject
                val displayName = obj?.string("name")
                    ?: obj?.string("gaia_name")
                    ?: id
                BrowserProfile(id = id, displayName = displayName)
            }
            .sortedWith(compareBy({ it.id != "Default" }, { it.id }))
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
