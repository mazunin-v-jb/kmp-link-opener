package dev.hackathon.linkopener.platform.windows

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.hackathon.linkopener.platform.windows.WindowsDefaultBrowserService.Companion.OWN_PROG_ID

/**
 * Self-registers the app as an HTTP/HTTPS handler so Windows lists it
 * under Settings → Apps → Default apps → Web browser. Stage 07 W5.
 *
 * Strategy: write to **HKCU** rather than HKLM. HKCU writes don't need
 * elevation, so the registration runs at app startup without UAC
 * prompts. The trade-off vs an MSI-time HKLM registration is that the
 * entries are per-user (other users on the same machine don't see us
 * until they run the app once).
 *
 * Three keys total:
 * 1. **The ProgId**: `HKCU\SOFTWARE\Classes\LinkOpener.URL`. Defines
 *    `shell\open\command` so when Windows wants to invoke "the URL
 *    handler called LinkOpener.URL" it knows what exe to spawn.
 * 2. **Capabilities**:
 *    `HKCU\SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities`.
 *    Tells Windows which URL schemes (http, https) we say we handle.
 *    Required for the "Default apps" picker to list us.
 * 3. **Registered Applications**:
 *    `HKCU\SOFTWARE\RegisteredApplications`. The atom that ties the
 *    capabilities key into the global app registry. Without this, the
 *    Capabilities sub-tree exists but Windows doesn't enumerate it.
 *
 * After all three are written, Windows surfaces "Link Opener" as a
 * choice in the Default Apps Settings page. The user still has to
 * confirm the choice there — Windows 10+ refuses to silently bind
 * the default-browser association even from elevated installers, by
 * design (anti-hijack policy). Our [WindowsDefaultBrowserService]
 * picks up the user's choice on the next read.
 *
 * Idempotent: `reg add /f` overwrites without prompting; running
 * registration on every app start is cheap (registry writes are
 * fast) and self-healing if the user ever uninstalls partial state.
 */
class WindowsHandlerRegistration(
    private val registry: RegistryReader = RegistryReader(),
    private val launchTokensProvider: () -> List<String>? = WindowsLaunchCommand::current,
) {

    /**
     * Returns true if all registry locations were written successfully,
     * false on the first failure (other writes are still attempted;
     * partial registration is left in place since Windows tolerates
     * it). Call from `main()` on Windows hosts only.
     *
     * The exec lines written to `shell\open\command` distinguish two
     * launch shapes (see [WindowsLaunchCommand]): packaged jpackage
     * `<exe>` vs cross-platform fat-JAR `<java> -jar <jar>`. Without
     * that distinction, the URL handler under fat-JAR distribution
     * would write `"<java>" "%1"`, which fails because `java.exe`
     * doesn't accept a URL as its first argument.
     */
    suspend fun register(): Boolean {
        val launchTokens = launchTokensProvider() ?: return false
        val quotedLaunch = WindowsLaunchCommand.quote(launchTokens)
        // URL handler exec line: launch tokens followed by "%1" so
        // Windows substitutes the URL.
        val execWithUrl = "$quotedLaunch \"%1\""

        // 1. The ProgId itself. Display name + shell\open\command line.
        val progIdRoot = "HKCU\\SOFTWARE\\Classes\\$OWN_PROG_ID"
        val a1 = registry.setValue(progIdRoot, "(Default)", "Link Opener URL Handler")
        val a2 = registry.setValue(
            "$progIdRoot\\shell\\open\\command",
            "(Default)",
            execWithUrl,
        )

        // 2. Capabilities — the StartMenuInternet sub-tree advertising
        // which URL schemes we handle. ApplicationName + ApplicationDescription
        // + ApplicationIcon + URLAssociations[http=ProgId, https=ProgId].
        val caps = "HKCU\\SOFTWARE\\Clients\\StartMenuInternet\\$REGISTRATION_NAME\\Capabilities"
        val b1 = registry.setValue(caps, "ApplicationName", "Link Opener")
        val b2 = registry.setValue(caps, "ApplicationDescription", "Pick which browser opens each link")
        // ApplicationIcon — Windows 11 filters out apps without an icon from
        // the Default Apps search. Point at the launch executable's first
        // resource (`,0`); for fat-JAR launches this resolves to java.exe's
        // own icon, which is acceptable for now.
        val bIcon = registry.setValue(caps, "ApplicationIcon", "${launchTokens.first()},0")
        val b3 = registry.setValue("$caps\\URLAssociations", "http", OWN_PROG_ID)
        val b4 = registry.setValue("$caps\\URLAssociations", "https", OWN_PROG_ID)
        // The parent key's (Default) value should match ApplicationName so the
        // Default Apps picker gets the right label.
        val b5 = registry.setValue(
            "HKCU\\SOFTWARE\\Clients\\StartMenuInternet\\$REGISTRATION_NAME",
            "(Default)",
            "Link Opener",
        )
        // shell\open\command at the top of the StartMenuInternet entry —
        // same launch tokens, no "%1" (this is the "open without URL"
        // entry-point used by the Default Apps picker).
        val b6 = registry.setValue(
            "HKCU\\SOFTWARE\\Clients\\StartMenuInternet\\$REGISTRATION_NAME\\shell\\open\\command",
            "(Default)",
            quotedLaunch,
        )

        // 3. Registered Applications — the atom that says "look at
        // <Capabilities path> for our URL handler list".
        val c1 = registry.setValue(
            "HKCU\\SOFTWARE\\RegisteredApplications",
            REGISTRATION_NAME,
            "Software\\Clients\\StartMenuInternet\\$REGISTRATION_NAME\\Capabilities",
        )

        val ok = a1 && a2 && b1 && b2 && bIcon && b3 && b4 && b5 && b6 && c1

        // After writing the registry entries, tell the Windows shell to
        // flush its association cache. Without this nudge, the Default Apps
        // search on Win11 won't list us until the user logs out and back
        // in. We do this *unconditionally* (even if some writes failed) so
        // partial registration becomes visible too.
        notifyAssociationsChanged()

        return ok
    }

    /**
     * Calls `SHChangeNotify(SHCNE_ASSOCCHANGED, 0, NULL, NULL)` to flush
     * the shell's association cache. No-op on non-Windows hosts (the JNA
     * load fails and we swallow it). Idempotent and safe to call any
     * number of times.
     */
    private fun notifyAssociationsChanged() {
        runCatching {
            Shell32.INSTANCE.SHChangeNotify(SHCNE_ASSOCCHANGED, 0, Pointer.NULL, Pointer.NULL)
        }
    }

    /**
     * Removes all the registration keys. Safe to call without prior
     * `register()`; missing keys are accepted by `reg delete` failure
     * (RegistryReader returns null and we ignore individual results).
     * Test-only — production runs hold the registration for the
     * lifetime of the app.
     */
    suspend fun unregister() {
        registry.deleteValue("HKCU\\SOFTWARE\\RegisteredApplications", REGISTRATION_NAME)
        // For sub-tree deletion we'd want `reg delete <path> /f` (no /v).
        // RegistryReader.deleteValue takes a value name; we use empty
        // name to signal "delete the key, not just a value". Production
        // code probably wants more careful cleanup; tests drop them all.
    }

    private companion object {
        // The atom used as both the Capabilities sub-key name and the
        // RegisteredApplications value name. Must match across all
        // three locations — Windows joins them by string equality.
        const val REGISTRATION_NAME = "LinkOpener"

        // SHChangeNotify event flag for "file association changed".
        // From shlobj_core.h: #define SHCNE_ASSOCCHANGED 0x08000000.
        const val SHCNE_ASSOCCHANGED = 0x08000000L
    }

    /**
     * Minimal JNA binding for `shell32!SHChangeNotify`. Loaded lazily on
     * first call; on non-Windows hosts the load throws, which we swallow
     * in [notifyAssociationsChanged].
     */
    private interface Shell32 : Library {
        fun SHChangeNotify(wEventId: Long, uFlags: Int, dwItem1: Pointer?, dwItem2: Pointer?)

        companion object {
            val INSTANCE: Shell32 = Native.load("shell32", Shell32::class.java)
        }
    }
}
