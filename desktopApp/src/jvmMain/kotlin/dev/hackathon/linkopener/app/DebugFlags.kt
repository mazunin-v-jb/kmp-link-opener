package dev.hackathon.linkopener.app

/**
 * Process-wide debug toggles read from JVM system properties at startup.
 * Set `-Dlinkopener.debug=true` in JVM args (IDE run config, `_JAVA_OPTIONS`,
 * or `./gradlew :desktopApp:run -Dlinkopener.debug=true`) to opt in. See
 * CLAUDE.md § "Debug logging" for the full list of what this gates.
 *
 * Single source of truth so renaming the flag or adding a fallback (env var,
 * build-time switch) only touches this file.
 */
internal object DebugFlags {
    val enabled: Boolean = System.getProperty("linkopener.debug") == "true"
}
