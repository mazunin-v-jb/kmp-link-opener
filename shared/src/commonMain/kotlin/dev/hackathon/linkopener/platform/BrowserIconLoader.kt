package dev.hackathon.linkopener.platform

/**
 * Returns the raw bytes of a browser application's icon as a PNG (or any
 * Skia-decodable encoding — `BrowserIconRepository` runs the decoder).
 *
 * Implementations key off [applicationPath] — the same path that
 * [dev.hackathon.linkopener.core.model.Browser.applicationPath] carries — so
 * the cache layer can deduplicate across multiple browsers that point at the
 * same `.app` / `.exe` (Chromium-family rows for different profiles share
 * one bundle on disk, for instance).
 *
 * Returns `null` when no icon could be extracted: missing file, unsupported
 * format, or a platform without a real implementation. Callers must handle
 * that case by falling back to the letter-square avatar.
 */
interface BrowserIconLoader {
    suspend fun load(applicationPath: String): ByteArray?
}

internal class NoOpBrowserIconLoader : BrowserIconLoader {
    override suspend fun load(applicationPath: String): ByteArray? = null
}
