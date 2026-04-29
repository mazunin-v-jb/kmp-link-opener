package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import dev.hackathon.linkopener.domain.detectBrowserFamilyByDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Reads metadata from a `.exe` for the manual-add flow on Windows.
 * Stage 07 W7.
 *
 * Strategy: shell out to PowerShell's `Get-Item` cmdlet, which exposes
 * the embedded PE version-info resource without us having to write a
 * pure-Java PE reader.
 *
 * Mirroring [dev.hackathon.linkopener.platform.macos.MacOsBrowserMetadataExtractor]
 * (which shells out to `plutil` for `Info.plist`), this returns:
 * - **Success(Browser)** when the file exists, ends in `.exe`, and
 *   PowerShell reports back at least a ProductName / FileDescription.
 * - **Failure(reason)** otherwise — UI surfaces the reason to the user
 *   in the manual-add notice banner.
 *
 * Note: the metadata captures *what the .exe says it is*. We don't
 * verify that it actually handles HTTP — Windows has no equivalent of
 * macOS's `CFBundleURLTypes` declaration on the binary itself. The
 * picker chain re-validates at launch time (a non-browser .exe will
 * just fail to do anything useful with the URL); this is the same
 * trust model as macOS manual-add.
 */
class WindowsBrowserMetadataExtractor(
    private val runner: (List<String>) -> String? = ::defaultRunner,
) : BrowserMetadataExtractor {

    override suspend fun extract(path: String): ExtractResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext ExtractResult.Failure("File not found: $path")
        if (!file.name.endsWith(".exe", ignoreCase = true)) {
            return@withContext ExtractResult.Failure("Not a Windows executable: ${file.name}")
        }

        // PowerShell's Get-Item exposes VersionInfo with the embedded PE
        // version resource fields. Format-List output is line-oriented
        // and trivially parseable.
        val output = runner(
            listOf(
                "powershell", "-NoProfile", "-Command",
                "(Get-Item -LiteralPath '${path.replace("'", "''")}').VersionInfo | " +
                    "Format-List ProductName,FileVersion,FileDescription",
            ),
        ) ?: return@withContext ExtractResult.Failure(
            "Could not read version info; ensure PowerShell is on PATH",
        )

        val fields = parseVersionInfo(output)
        val displayName = fields["FileDescription"]?.takeIf { it.isNotBlank() }
            ?: fields["ProductName"]?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val version = fields["FileVersion"]?.takeIf { it.isNotBlank() }

        ExtractResult.Success(
            Browser(
                // Windows has no reverse-DNS bundle id; use the exe filename
                // (without `.exe`) as the stable atom. Same shape as
                // [WindowsBrowserDiscovery]'s use of the StartMenuInternet
                // sub-key name.
                bundleId = file.nameWithoutExtension,
                displayName = displayName,
                applicationPath = file.absolutePath,
                version = version,
                family = detectBrowserFamilyByDisplayName(displayName),
            ),
        )
    }

    companion object {

        /**
         * Parses `Format-List`'s `Key : Value` output. Multi-line values
         * are not handled (version-info fields are short strings).
         */
        fun parseVersionInfo(output: String): Map<String, String> = output
            .lineSequence()
            .map { it.trim() }
            .filter { it.contains(':') }
            .associate { line ->
                val (key, value) = line.split(":", limit = 2)
                key.trim() to value.trim()
            }

        private fun defaultRunner(args: List<String>): String? = runCatching {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream, Charset.defaultCharset()))
                .use { it.readText() }
            if (process.waitFor() != 0) return@runCatching null
            output
        }.getOrNull()
    }
}
