package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

open class PlistJsonParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    open fun parseBrowser(jsonText: String, applicationPath: Path): Browser? {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return null

        if (!handlesHttpOrHttps(root)) return null

        return browserFrom(root, applicationPath)
    }

    /**
     * Same metadata extraction as [parseBrowser] but without the http/https
     * URL-types gate. Used for manually-added browsers — the extractor calls
     * [isLinkHandler] separately so it can surface "missing CFBundleIdentifier"
     * vs. "not a link handler" as distinct error reasons.
     */
    open fun parseAnyApp(jsonText: String, applicationPath: Path): Browser? {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return null
        return browserFrom(root, applicationPath)
    }

    /**
     * Returns true iff the plist declares `http` or `https` under
     * `CFBundleURLTypes[].CFBundleURLSchemes` — the same predicate
     * `MacOsBrowserDiscovery` uses to classify auto-discovered apps as
     * browsers, so manual addition stays consistent with discovery.
     */
    open fun isLinkHandler(jsonText: String): Boolean {
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return false
        return handlesHttpOrHttps(root)
    }

    private fun browserFrom(root: JsonObject, applicationPath: Path): Browser? {
        val bundleId = root.string("CFBundleIdentifier") ?: return null

        val displayName = root.string("CFBundleDisplayName")
            ?: root.string("CFBundleName")
            ?: applicationPath.nameWithoutExtension

        val version = root.string("CFBundleShortVersionString")
            ?: root.string("CFBundleVersion")

        return Browser(
            bundleId = bundleId,
            displayName = displayName,
            applicationPath = applicationPath.toString(),
            version = version,
        )
    }

    private fun handlesHttpOrHttps(root: JsonObject): Boolean {
        val urlTypes = root["CFBundleURLTypes"] as? JsonArray ?: return false
        for (entry in urlTypes) {
            val obj = entry as? JsonObject ?: continue
            val schemes = obj["CFBundleURLSchemes"] as? JsonArray ?: continue
            for (scheme in schemes) {
                val text = scheme.jsonPrimitive.contentOrNull?.lowercase() ?: continue
                if (text == "http" || text == "https") return true
            }
        }
        return false
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
