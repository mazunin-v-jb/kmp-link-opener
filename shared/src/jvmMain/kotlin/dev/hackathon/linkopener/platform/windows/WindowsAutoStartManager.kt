package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.platform.AutoStartManager

/**
 * Enables / disables "Start at login" by writing the app's exe path
 * to `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` under a
 * stable value name. Per-user (HKCU) so we don't need elevation —
 * any user can toggle their own autostart.
 *
 * Registry layout when enabled:
 * ```
 * HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run
 *     LinkOpener    REG_SZ    "C:\Program Files\Link Opener\Link Opener.exe"
 * ```
 *
 * Resolves the current exe path via [exePathProvider], which defaults
 * to `ProcessHandle.current().info().command()`. Tests inject a
 * fixed string. The path is wrapped in quotes when written so paths
 * with spaces (Program Files) survive.
 *
 * Failure modes: any `reg.exe` failure (permissions denied, bogus
 * argv) is reported by [setEnabled] as a no-op — `RegistryReader`
 * already swallows non-zero exits as null. The user sees no exception;
 * a follow-up call to [isEnabled] would return false, so the UI
 * toggle visually flips back. Accept this as best-effort UI signal.
 */
class WindowsAutoStartManager(
    private val registry: RegistryReader = RegistryReader(),
    private val launchTokensProvider: () -> List<String>? = WindowsLaunchCommand::current,
) : AutoStartManager {

    override suspend fun isEnabled(): Boolean = registry.queryValue(RUN_KEY, VALUE_NAME) != null

    override suspend fun setEnabled(enabled: Boolean) {
        if (enabled) {
            val tokens = launchTokensProvider() ?: return
            // [WindowsLaunchCommand.quote] handles both packaged `<exe>`
            // and fat-JAR `<java> -jar <jar>` shapes — see that class
            // for why this matters.
            registry.setValue(RUN_KEY, VALUE_NAME, WindowsLaunchCommand.quote(tokens))
        } else {
            registry.deleteValue(RUN_KEY, VALUE_NAME)
        }
    }

    companion object {
        // HKCU is per-user; HKLM\…\Run requires admin rights. Per-user
        // is the right default — autostart shouldn't escalate.
        private const val RUN_KEY = "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"

        // Stable value name across upgrades. Uninstaller should clean
        // this up; if it doesn't, a stale value just spawns a missing
        // exe at login (Windows shows a tiny "couldn't find" toast).
        private const val VALUE_NAME = "LinkOpener"
    }
}
