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
