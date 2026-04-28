package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult

/**
 * Fallback for OSes whose manual-browser flow hasn't been wired yet (Windows /
 * Linux are stage 7 and 8). Always reports failure so the UI doesn't pretend
 * the addition succeeded.
 */
class UnsupportedManualBrowserExtractor(
    private val osName: String = System.getProperty("os.name").orEmpty(),
) : BrowserMetadataExtractor {
    override suspend fun extract(path: String): ExtractResult =
        ExtractResult.Failure("Manual browser addition is not yet supported on ${osName.ifBlank { "this platform" }}")
}
