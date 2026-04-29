package dev.hackathon.linkopener.platform.windows

/**
 * Resolves the command-line that should be written to the Windows
 * registry so the OS can re-launch us — either as a URL handler
 * (`shell\open\command`) or via auto-start (`Run` key).
 *
 * Two launch shapes need to be distinguished:
 * - **Packaged install** (jpackage / WiX MSI): `ProcessHandle.current()`
 *   reports the bundled `.exe` path with no JVM-level arguments. The
 *   exec line is just `"<exe>"` plus optional `"%1"` for URL handler.
 * - **Cross-platform fat-JAR** (`java -jar link-opener.jar`):
 *   `ProcessHandle.current()` reports `java.exe` plus
 *   `["-jar", "<jar>"]` arguments. We need to write the full
 *   `"<java>" -jar "<jar>"` line so Windows knows how to spawn us
 *   again.
 *
 * Detection is via `ProcessHandle.current().info().arguments()` — if
 * `-jar <path>` is in the argv, it's a fat-JAR launch.
 */
internal object WindowsLaunchCommand {

    /**
     * Returns the launch tokens — either `["<java>", "-jar", "<jar>"]`
     * or `["<exe>"]`. Null if `ProcessHandle.current().info()` doesn't
     * expose the command (rare; some restricted JVM environments).
     */
    fun current(): List<String>? = currentProcessInfo()?.let { (cmd, args) ->
        val jarFlag = args.indexOf("-jar")
        if (jarFlag >= 0 && jarFlag + 1 < args.size) {
            listOf(cmd, "-jar", args[jarFlag + 1])
        } else {
            listOf(cmd)
        }
    }

    /**
     * Joins tokens with Windows-style quoting suitable for a registry
     * `shell\open\command` `(Default)` value: each path-shaped token is
     * wrapped in double-quotes (so spaces in `Program Files` survive),
     * `-jar` is left bare.
     *
     * Example: `quote(listOf("C:\\Program Files\\Java\\java.exe", "-jar",
     * "C:\\Users\\u\\app.jar"))` →
     * `"C:\Program Files\Java\java.exe" -jar "C:\Users\u\app.jar"`.
     */
    fun quote(tokens: List<String>): String =
        tokens.joinToString(" ") { token ->
            if (token == "-jar") token else "\"$token\""
        }

    private fun currentProcessInfo(): Pair<String, List<String>>? {
        val info = ProcessHandle.current().info()
        val command = info.command().orElse(null) ?: return null
        val args = info.arguments()
            .map { it.toList() }
            .orElse(emptyList())
        return command to args
    }
}
