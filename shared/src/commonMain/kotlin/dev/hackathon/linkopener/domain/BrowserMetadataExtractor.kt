package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.Browser

/**
 * Reads metadata for an arbitrary application path so we can register it as a
 * manually-added browser. Per-OS implementations live in jvmMain (`MacOsBrowserMetadataExtractor`,
 * Windows / Linux stubs).
 */
interface BrowserMetadataExtractor {
    suspend fun extract(path: String): ExtractResult

    sealed interface ExtractResult {
        data class Success(val browser: Browser) : ExtractResult
        data class Failure(val reason: String) : ExtractResult
    }
}
