package dev.hackathon.linkopener.platform.windows

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Minimal wrapper around Windows' built-in `reg.exe` for reading
 * registry data. Strategy A from `ai_stages/07_windows_support/plan.md`:
 * mirrors how the macOS code shells out to `plutil` / `sips` / `open`.
 *
 * `reg.exe` ships with every Windows install since Windows 2000 — no
 * native plumbing, no JNA, no `jna-platform` dependency. Each query is
 * a ~50ms subprocess; results are cached one level up (in
 * [WindowsBrowserDiscovery] via `BrowserRepositoryImpl`'s normal cache,
 * in [WindowsAutoStartManager] / [WindowsDefaultBrowserService] via the
 * fact that they're invoked at most once per user action).
 *
 * Output format expectations (English-locale `reg.exe`):
 *
 * ```
 * HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet
 *
 * HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome
 *     (Default)    REG_SZ    Google Chrome
 *
 * HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome\shell\open\command
 *     (Default)    REG_SZ    "C:\Program Files\Google\Chrome\Application\chrome.exe"
 * ```
 *
 * Encoding: `reg.exe` writes its output in the system's OEM code page,
 * not UTF-8. We use `Charset.defaultCharset()` because that's what the
 * JVM picks up for subprocess stdout on Windows; on a non-Windows host
 * (where this file would only be exercised by tests against captured
 * fixtures) it doesn't matter.
 *
 * Errors: any non-zero exit code, any failure to launch `reg.exe`, any
 * I/O exception → return null / empty list. Callers must treat absent
 * registry entries as "not configured" rather than as an error.
 */
class RegistryReader(
    private val runner: (List<String>) -> String? = ::defaultRunner,
) {

    /**
     * Returns the text body of `reg query <path>` (one or more recursive
     * `/s` levels depending on [recursive]), or null if the call failed
     * or the key doesn't exist. Caller parses with the helpers below.
     */
    suspend fun query(path: String, recursive: Boolean = false): String? = withContext(Dispatchers.IO) {
        runner(buildList {
            add("reg")
            add("query")
            add(path)
            if (recursive) add("/s")
        })
    }

    /**
     * Returns the value of `reg query <path> /v <name>`, or null if the
     * value doesn't exist. Tries the `(Default)` value when [name] is
     * empty.
     */
    suspend fun queryValue(path: String, name: String = ""): String? = withContext(Dispatchers.IO) {
        // `reg.exe` distinguishes the default (unnamed) value from named
        // ones at the argv level: `/ve` queries the default, `/v <name>`
        // queries a named value. Passing `/v "(Default)"` (the literal
        // string) makes reg.exe look for a value LITERALLY named
        // "(Default)" — which never exists; the default value is
        // actually unnamed. The output formats are identical (both
        // print the data line as `(Default)    REG_SZ    <data>`), so
        // the parser doesn't need to change.
        val args = buildList {
            add("reg")
            add("query")
            add(path)
            if (name.isEmpty()) {
                add("/ve")
            } else {
                add("/v")
                add(name)
            }
        }
        val output = runner(args) ?: return@withContext null
        parseSingleValue(output, name)
    }

    /**
     * `reg add <path> /v <name> /t <type> /d <data> /f`. The /f flag
     * skips the interactive overwrite prompt. Returns true on success.
     * Used by [WindowsAutoStartManager] to write
     * `HKCU\…\Run\LinkOpener`.
     */
    suspend fun setValue(
        path: String,
        name: String,
        data: String,
        type: String = "REG_SZ",
    ): Boolean = withContext(Dispatchers.IO) {
        runner(listOf("reg", "add", path, "/v", name, "/t", type, "/d", data, "/f")) != null
    }

    /**
     * `reg delete <path> /v <name> /f`. Returns true if the value was
     * deleted *or* didn't exist (idempotent — both states are "name is
     * not in the registry"). Returns false if `reg.exe` reported an
     * unrelated error (permissions, invalid path).
     */
    suspend fun deleteValue(path: String, name: String): Boolean = withContext(Dispatchers.IO) {
        runner(listOf("reg", "delete", path, "/v", name, "/f")) != null
    }

    companion object {

        /**
         * Default runner — spawns `reg.exe` and captures stdout in the
         * system's default charset (OEM code page on Windows). Returns
         * null on non-zero exit, on launch failure, or on I/O errors,
         * so callers can treat "absent" the same as "errored".
         */
        private fun defaultRunner(args: List<String>): String? = runCatching {
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream, Charset.defaultCharset()))
                .use { it.readText() }
            if (process.waitFor() != 0) return@runCatching null
            output
        }.getOrNull()

        /**
         * Lists immediate sub-key names directly under [parentPath].
         *
         * reg.exe accepts hive aliases (`HKLM`, `HKCU`, …) in input but
         * normalises its OUTPUT to the full form (`HKEY_LOCAL_MACHINE`,
         * `HKEY_CURRENT_USER`, …). So if the caller passes the short
         * form, we have to expand it before matching the prefix in the
         * output — otherwise no lines match and the sub-key list comes
         * back empty.
         */
        fun parseSubKeys(output: String, parentPath: String): List<String> {
            val prefix = expandHive(parentPath) + "\\"
            return output.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith(prefix) && it.length > prefix.length }
                // Direct children only — drop any line that contains a deeper
                // separator after the immediate sub-key segment.
                .map { it.substring(prefix.length) }
                .filter { '\\' !in it }
                .toList()
        }

        /**
         * Expands a registry path's leading hive alias to its full form.
         * `HKLM\…` → `HKEY_LOCAL_MACHINE\…`, etc. Already-full paths are
         * returned unchanged. Used by [parseSubKeys] to match reg.exe's
         * output normalisation.
         */
        internal fun expandHive(path: String): String {
            val mapping = listOf(
                "HKLM\\" to "HKEY_LOCAL_MACHINE\\",
                "HKCU\\" to "HKEY_CURRENT_USER\\",
                "HKCR\\" to "HKEY_CLASSES_ROOT\\",
                "HKU\\" to "HKEY_USERS\\",
                "HKCC\\" to "HKEY_CURRENT_CONFIG\\",
            )
            for ((short, full) in mapping) {
                if (path.startsWith(short, ignoreCase = true)) {
                    return full + path.substring(short.length)
                }
            }
            return path
        }

        /**
         * Returns the value of `(Default)` (when [name] is empty) or the
         * value of [name] from a `reg query .. /v <name>` payload.
         *
         * Lines look like:
         * ```
         *     (Default)    REG_SZ    "C:\Program Files\Google\..."
         *     ProgId       REG_SZ    LinkOpener.URL
         * ```
         *
         * The two/three columns are separated by *runs of whitespace*
         * (typically four spaces, but the count varies); split on
         * `\s{2,}` to parse robustly. The data column is returned
         * verbatim — surrounding quotes (if any) are NOT stripped here,
         * because for `shell\open\command` values the leading `"` is
         * part of a quoted-path token followed by un-quoted argv (e.g.
         * `"path" -osint "%1"`), and stripping both ends mangles the
         * remainder. Callers that want a clean path use
         * [WindowsBrowserDiscovery.stripCommandSuffix].
         */
        fun parseSingleValue(output: String, name: String): String? {
            val target = name.ifEmpty { "(Default)" }
            return output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("HKEY_") }
                .map { it.split(Regex("\\s{2,}"), limit = 3) }
                .filter { it.size == 3 }
                .firstOrNull { it[0].equals(target, ignoreCase = true) }
                ?.get(2)
                ?.trim()
        }
    }
}
