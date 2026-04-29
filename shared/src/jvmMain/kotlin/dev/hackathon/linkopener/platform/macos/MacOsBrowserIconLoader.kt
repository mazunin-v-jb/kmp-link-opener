package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.platform.BrowserIconLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts the icon for a macOS browser by reading its `Info.plist` for the
 * `CFBundleIconFile` reference, locating the bundled `.icns` under
 * `Contents/Resources/`, and converting that to PNG bytes via `sips`
 * (built into macOS — no extra runtime deps).
 *
 * Why not [dev.hackathon.linkopener.platform.FileSystemViewBrowserIconLoader]
 * here? `.app` bundles are directories on disk, so `FileSystemView` returns
 * the generic Finder folder icon for them — that's why the first attempt
 * showed folder icons instead of browser icons. Going through the bundle's
 * own resources is the documented Apple-style path.
 *
 * Two subprocess calls per browser (`plutil` for plist → JSON, `sips` for
 * `.icns` → PNG). Each is ~tens of ms; prefetch is async so the UI never
 * waits on it. Results land in `BrowserIconRepository`'s in-memory cache for
 * the rest of the process lifetime.
 */
internal class MacOsBrowserIconLoader(
    private val plutilRunner: PlutilRunner = PlutilRunner(),
    private val plistParser: PlistJsonParser = PlistJsonParser(),
    private val sipsExecutable: String = DEFAULT_SIPS_EXECUTABLE,
    private val sipsTimeoutSeconds: Long = DEFAULT_SIPS_TIMEOUT_SECONDS,
    private val targetSize: Int = DEFAULT_TARGET_SIZE,
) : BrowserIconLoader {

    override suspend fun load(applicationPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val appPath = Path.of(applicationPath)
        val plist = appPath.resolve("Contents").resolve("Info.plist")
        if (!plist.exists()) return@withContext null

        val plistJson = plutilRunner.toJson(plist) ?: return@withContext null
        val iconFileName = plistParser.parseIconFileName(plistJson) ?: return@withContext null
        val icnsPath = resolveIcnsPath(appPath, iconFileName)
        if (!icnsPath.exists()) return@withContext null

        convertIcnsToPng(icnsPath)
    }

    private fun resolveIcnsPath(appPath: Path, iconFileName: String): Path {
        // CFBundleIconFile may or may not include the `.icns` extension —
        // Apple's spec is explicit that both forms are valid. Add it if
        // missing so `Contents/Resources/AppIcon` resolves to
        // `Contents/Resources/AppIcon.icns`.
        val resolved = if (iconFileName.endsWith(".icns", ignoreCase = true)) {
            iconFileName
        } else {
            "$iconFileName.icns"
        }
        return appPath.resolve("Contents").resolve("Resources").resolve(resolved)
    }

    private fun convertIcnsToPng(icnsPath: Path): ByteArray? {
        // `sips -Z <size>` resamples the icon to the target dimension while
        // preserving aspect ratio, picking the closest representation from
        // the multi-resolution `.icns` and downscaling/upscaling as needed.
        // 128px gives 4× retina headroom for our 32dp display slot.
        val tmpDir = Files.createTempDirectory("link-opener-icon-")
        val tmpPng = tmpDir.resolve("icon.png")
        return try {
            val process = ProcessBuilder(
                sipsExecutable,
                "-s", "format", "png",
                icnsPath.toString(),
                "--out", tmpPng.toString(),
                "-Z", targetSize.toString(),
            ).redirectErrorStream(true).start()

            val finished = process.waitFor(sipsTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0) return null
            if (!tmpPng.exists()) return null
            Files.readAllBytes(tmpPng)
        } catch (_: Exception) {
            null
        } finally {
            // Best-effort cleanup; if a stray file leaks into the temp dir
            // the OS will GC it anyway.
            runCatching { Files.deleteIfExists(tmpPng) }
            runCatching { Files.deleteIfExists(tmpDir) }
        }
    }

    companion object {
        const val DEFAULT_SIPS_EXECUTABLE: String = "/usr/bin/sips"
        const val DEFAULT_SIPS_TIMEOUT_SECONDS: Long = 5
        // 128px is the largest "common" icon size in `.icns` files (32, 48,
        // 64, 128, 256, 512). Picking 128 means sips can usually copy a
        // representation directly without resampling. Cached PNG payload
        // stays well under 64 KB even at this size.
        const val DEFAULT_TARGET_SIZE: Int = 128
    }
}
