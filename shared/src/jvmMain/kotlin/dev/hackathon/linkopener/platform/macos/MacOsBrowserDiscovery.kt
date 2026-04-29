package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.domain.chromiumUserDataPaths
import dev.hackathon.linkopener.domain.detectBrowserFamily
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path

class MacOsBrowserDiscovery(
    private val scanner: AppBundleScanner = AppBundleScanner(),
    private val plistReader: InfoPlistReader = InfoPlistReader(),
    private val searchRoots: List<Path> = defaultSearchRoots(),
    private val profileScanner: ChromiumProfileScanner = ChromiumProfileScanner(),
    private val applicationSupportDir: Path = defaultApplicationSupportDir(),
) : BrowserDiscovery {

    override suspend fun discover(): List<Browser> = withContext(Dispatchers.IO) {
        val candidates = findCandidatePaths()
        val parents = readBrowsersInParallel(candidates)
        // For each parent browser, classify by family and (for Chromium with
        // ≥2 profiles) expand into one record per profile. Single-profile
        // Chromium and non-Chromium browsers stay as a single record without
        // a `profile` field — see plan § Q2 (don't dilute the list with
        // synthetic "Default" entries).
        parents
            .flatMap { expandWithProfiles(it) }
            .sortedBy { it.displayName.lowercase() }
    }

    /**
     * Walks every [searchRoots] entry for `.app` bundles, canonicalises the
     * resolved paths (so a symlink and its target dedupe), and returns each
     * unique path once. Result order is roots-then-bundle-order — the parallel
     * plist read in [readBrowsersInParallel] doesn't depend on order.
     */
    private fun findCandidatePaths(): List<Path> = searchRoots
        .flatMap { scanner.findAppBundles(it) }
        .map { resolveSafely(it) }
        .distinct()

    /**
     * Reads the `Info.plist` of every candidate concurrently (each plist read
     * shells out to `plutil` — single-threaded would serialise ~1-3 seconds per
     * browser), drops the null returns (not-a-browser / unreadable plist), and
     * dedupes by [Browser.applicationPath] so the same bundle surfacing under
     * two roots (e.g. `/System/Applications` + symlinked alias) only appears
     * once.
     */
    private suspend fun readBrowsersInParallel(paths: List<Path>): List<Browser> = coroutineScope {
        paths.map { path -> async { plistReader.readBrowser(path) } }.awaitAll()
    }
        .filterNotNull()
        .distinctBy { it.applicationPath }

    private fun expandWithProfiles(parent: Browser): List<Browser> {
        val family = detectBrowserFamily(parent.bundleId)
        val parentWithFamily = parent.copy(family = family)
        if (family != BrowserFamily.Chromium) return listOf(parentWithFamily)

        val userDataPath = chromiumUserDataPaths[parent.bundleId] ?: return listOf(parentWithFamily)
        val userDataDir = applicationSupportDir.resolve(userDataPath)
        val profiles = profileScanner.scan(userDataDir)
        return when {
            profiles.size >= 2 -> profiles.map { profile ->
                parentWithFamily.copy(profile = profile)
            }
            else -> listOf(parentWithFamily)
        }
    }

    private fun resolveSafely(path: Path): Path =
        runCatching { path.toRealPath() }.getOrElse { path }

    private companion object {
        fun defaultSearchRoots(): List<Path> = listOf(
            Path("/Applications"),
            Path(System.getProperty("user.home"), "Applications"),
            Path("/System/Applications"),
            Path("/System/Volumes/Preboot/Cryptexes/App/System/Applications"),
        )

        fun defaultApplicationSupportDir(): Path = Path(
            System.getProperty("user.home").orEmpty(),
            "Library",
            "Application Support",
        )
    }
}
