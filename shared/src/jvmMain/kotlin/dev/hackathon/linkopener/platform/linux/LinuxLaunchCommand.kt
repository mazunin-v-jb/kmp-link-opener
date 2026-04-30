package dev.hackathon.linkopener.platform.linux

/**
 * Resolves the command-line that should be used to re-launch the app
 * — either as a URL handler from the registered `.desktop` file or via
 * an autostart entry. Symmetric to `WindowsLaunchCommand` on the
 * Windows side.
 *
 * Two launch shapes:
 * - **Packaged install** (DEB / jpackage): `ProcessHandle.current()`
 *   reports the bundled native launcher with no JVM flags. The
 *   resulting tokens are `["<launcher>"]`.
 * - **Cross-platform fat-JAR** (`java -jar link-opener.jar`):
 *   tokens are `["<java>", "-jar", "<jar>"]`.
 *
 * Detection mirrors the Windows side: probe argv for `-jar` and pick
 * the next token as the jar path.
 */
internal object LinuxLaunchCommand {

    fun current(): List<String>? = currentProcessInfo()?.let { (cmd, args) ->
        val jarFlag = args.indexOf("-jar")
        if (jarFlag >= 0 && jarFlag + 1 < args.size) {
            listOf(cmd, "-jar", args[jarFlag + 1])
        } else {
            listOf(cmd)
        }
    }

    /**
     * Joins tokens for a `Exec=` line in a `.desktop` file: paths
     * containing spaces are wrapped in double quotes per the
     * freedesktop spec. `-jar` is left bare. URL field code (`%u`)
     * is appended by the caller when needed.
     */
    fun quote(tokens: List<String>): String =
        tokens.joinToString(" ") { token ->
            when {
                token == "-jar" -> token
                ' ' in token -> "\"$token\""
                else -> token
            }
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
