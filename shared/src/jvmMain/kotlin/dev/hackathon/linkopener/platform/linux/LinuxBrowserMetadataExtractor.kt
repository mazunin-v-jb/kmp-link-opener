package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import dev.hackathon.linkopener.domain.detectBrowserFamilyByDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads a user-picked `.desktop` file to populate a [Browser] for the
 * manual-add flow. Mirrors the macOS / Windows extractors: the user has
 * pointed us at a specific entry and we trust the choice — but we still
 * gate on the same MimeType filter discovery uses, so adding "GIMP" by
 * mistake fails fast with a clear reason.
 *
 * Reuses [readDesktopEntry] / [LinuxBrowserDiscovery] semantics so
 * manual-add and auto-discovery agree on what counts as a browser.
 */
internal class LinuxBrowserMetadataExtractor : BrowserMetadataExtractor {

    override suspend fun extract(path: String): ExtractResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) {
            return@withContext ExtractResult.Failure("Path does not exist: $path")
        }
        if (!file.isFile || !file.name.endsWith(".desktop")) {
            return@withContext ExtractResult.Failure("Not a .desktop file: $path")
        }
        val entry = readDesktopEntry(file)
            ?: return@withContext ExtractResult.Failure("File is not a valid .desktop entry")

        if (entry.string("Type") != "Application") {
            return@withContext ExtractResult.Failure("Entry is not Type=Application")
        }
        val mimeTypes = entry.semicolonList("MimeType")
        if (mimeTypes.none {
                it == LinuxBrowserDiscovery.HTTP_SCHEME ||
                    it == LinuxBrowserDiscovery.HTTPS_SCHEME
            }
        ) {
            return@withContext ExtractResult.Failure(
                "Entry does not declare itself as an http/https handler",
            )
        }
        // Bare Name=, no locale suffix — matches LinuxBrowserDiscovery so
        // a manually-added entry shows the same display name as one
        // auto-discovered from the same `.desktop` file.
        val displayName = entry.string("Name")
            ?: return@withContext ExtractResult.Failure("Entry is missing the Name= key")
        if (entry.string("Exec").isNullOrBlank()) {
            return@withContext ExtractResult.Failure("Entry is missing the Exec= key")
        }

        val canonical = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
        val bundleId = file.name.substringBeforeLast(".desktop")
        ExtractResult.Success(
            Browser(
                bundleId = bundleId,
                displayName = displayName,
                applicationPath = canonical,
                version = null,
                family = detectBrowserFamilyByDisplayName(displayName),
            ),
        )
    }
}
