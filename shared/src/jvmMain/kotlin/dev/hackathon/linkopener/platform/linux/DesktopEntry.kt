package dev.hackathon.linkopener.platform.linux

import java.io.File

/**
 * Tiny freedesktop `.desktop` file parser. Produces a flat key-value map
 * for the `[Desktop Entry]` group only — sub-groups like
 * `[Desktop Action <name>]` (browser actions, e.g. "Open new private
 * window") are intentionally ignored: discovery only cares about the
 * primary entry, and per-action entries don't have their own MimeType
 * declarations anyway.
 *
 * Locale-aware string lookups (`Name`, `GenericName`, `Comment`) follow
 * the spec's `Name[ru_RU.UTF-8]` -> `Name[ru_RU]` -> `Name[ru]` -> `Name`
 * fallback chain. We only honor language + country granularity; modifier
 * suffixes (`@latin` etc.) are rare and handled by string equality if
 * the caller asks for the exact tag.
 *
 * Field-code stripping for `Exec=` lives separately in
 * [stripExecFieldCodes] — discovery preserves the raw `Exec`, the
 * launcher tokenises and strips when it's about to spawn the process.
 */
internal data class DesktopEntry(
    private val rawValues: Map<String, String>,
) {
    fun string(key: String): String? = rawValues[key]?.takeIf { it.isNotEmpty() }

    /**
     * Returns the locale-suffixed value for [key] honouring [locale]
     * (a BCP-47 / POSIX language tag like "ru" or "ru_RU"). Falls back
     * to the bare `key` when no localised match is found.
     */
    fun localizedString(key: String, locale: String): String? {
        if (locale.isNotEmpty()) {
            // Try progressively shorter locale suffixes: ru_RU.UTF-8 ->
            // ru_RU -> ru. The spec also allows a `@modifier` part; we
            // ignore it (rare in browser .desktop files).
            val parts = locale.substringBefore('.').split('_')
            for (i in parts.size downTo 1) {
                val tag = parts.take(i).joinToString("_")
                val key = "$key[$tag]"
                rawValues[key]?.takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return string(key)
    }

    fun semicolonList(key: String): List<String> =
        string(key)?.split(';')?.filter { it.isNotEmpty() } ?: emptyList()

    fun boolean(key: String, default: Boolean = false): Boolean =
        when (string(key)?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
        }
}

/**
 * Reads [desktopFile] and returns the `[Desktop Entry]` group as a
 * [DesktopEntry]. Returns null when the file isn't a valid .desktop
 * (no `[Desktop Entry]` section at all). I/O errors propagate.
 *
 * `.desktop` is a simple INI dialect:
 *  - Group headers in square brackets.
 *  - `key=value` lines (no spaces around `=` per spec, but real-world
 *    files sometimes have them — we trim).
 *  - `#` line comments.
 *  - Blank lines ignored.
 */
internal fun readDesktopEntry(desktopFile: File): DesktopEntry? {
    val values = mutableMapOf<String, String>()
    var inMainGroup = false
    desktopFile.bufferedReader().useLines { lines ->
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith("[")) {
                inMainGroup = line == "[Desktop Entry]"
                continue
            }
            if (!inMainGroup) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            // Don't overwrite earlier entries with later ones for the
            // same key — the spec says first occurrence wins.
            values.putIfAbsent(key, value)
        }
    }
    return if (values.isEmpty()) null else DesktopEntry(values)
}

/**
 * Tokenises an `Exec=` line into argv following the freedesktop spec's
 * shell-style quoting and strips the field codes (`%u %U %f %F %i %c
 * %k`) the spec allows. Field codes are removed wholesale because we
 * substitute our own URL — keeping `%u` would either pass it literally
 * or trip a browser that doesn't recognise field-code expansion.
 *
 * Quoting rules per spec (§ Exec key):
 *  - Double-quoted segments preserve spaces.
 *  - Inside quotes, `\\` -> `\`, `\"` -> `"`, `\$` -> `$`, `` \` `` -> `` ` ``.
 *  - `%%` -> literal `%`.
 *
 * Implementation note: we don't aim for perfect spec conformance —
 * real-world browser `.desktop` files have very plain `Exec=` lines
 * (`firefox %u`, `/opt/google/chrome/google-chrome %U`); the parser
 * handles those plus the quoted-with-spaces case (`"My App/launcher" %U`).
 */
internal fun stripExecFieldCodes(exec: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < exec.length) {
        val c = exec[i]
        when {
            c == '"' -> inQuotes = !inQuotes
            c == ' ' && !inQuotes -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            c == '\\' && i + 1 < exec.length -> {
                // \\ -> \, \" -> ", \$ -> $, \` -> `; otherwise keep
                // both chars (degenerate case — bash would error here).
                val next = exec[i + 1]
                when (next) {
                    '\\', '"', '$', '`' -> current.append(next)
                    else -> {
                        current.append(c)
                        current.append(next)
                    }
                }
                i++
            }
            c == '%' && i + 1 < exec.length -> {
                val next = exec[i + 1]
                if (next == '%') {
                    current.append('%')
                } // else: drop the field code entirely (`%u`, `%U`, `%f`, etc.)
                i++
            }
            else -> current.append(c)
        }
        i++
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}
