package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
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
) : BrowserDiscovery {

    override suspend fun discover(): List<Browser> = withContext(Dispatchers.IO) {
        val candidates = searchRoots
            .flatMap { scanner.findAppBundles(it) }
            .map { resolveSafely(it) }
            .distinct()

        val browsers = coroutineScope {
            candidates.map { path -> async { plistReader.readBrowser(path) } }.awaitAll()
        }

        browsers
            .filterNotNull()
            .distinctBy { it.applicationPath }
            .sortedBy { it.displayName.lowercase() }
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
    }
}
