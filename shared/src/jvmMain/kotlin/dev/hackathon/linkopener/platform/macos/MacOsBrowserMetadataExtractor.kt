package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Reads `Contents/Info.plist` from a user-picked `.app` bundle to populate a
 * [dev.hackathon.linkopener.core.model.Browser]. Used by the manual-add flow,
 * not by automatic discovery — this path skips the http/https URL-types check
 * because the user has explicitly asserted the app is a link-opener.
 */
class MacOsBrowserMetadataExtractor(
    private val plutilRunner: PlutilRunner = PlutilRunner(),
    private val parser: PlistJsonParser = PlistJsonParser(),
) : BrowserMetadataExtractor {

    override suspend fun extract(path: String): ExtractResult = withContext(Dispatchers.IO) {
        val appPath: Path = Path(path)
        if (!appPath.exists()) {
            return@withContext ExtractResult.Failure("Path does not exist: $path")
        }
        if (!appPath.isDirectory() || !appPath.toString().endsWith(".app")) {
            return@withContext ExtractResult.Failure("Not a macOS .app bundle: $path")
        }
        val plist = appPath.resolve("Contents").resolve("Info.plist")
        if (!plist.exists()) {
            return@withContext ExtractResult.Failure("Missing Info.plist inside bundle")
        }
        val jsonText = plutilRunner.toJson(plist)
            ?: return@withContext ExtractResult.Failure("plutil failed to read Info.plist")
        val browser = parser.parseAnyApp(jsonText, appPath)
            ?: return@withContext ExtractResult.Failure("Info.plist missing CFBundleIdentifier")
        if (!parser.isLinkHandler(jsonText)) {
            return@withContext ExtractResult.Failure(
                "App does not declare itself as an http/https handler",
            )
        }
        ExtractResult.Success(browser)
    }
}
