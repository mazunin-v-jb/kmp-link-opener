package dev.hackathon.linkopener.platform.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import dev.hackathon.linkopener.platform.DefaultBrowserService

/**
 * Detects whether *this* app is currently the default browser and deep-links
 * to the Default Apps settings page so the user can change it.
 *
 * Detection: `resolveActivity(ACTION_VIEW http, MATCH_DEFAULT_ONLY)` returns
 * the activity the system will route to. If its package matches ours, we're
 * the default. Works on every supported API (26+) — `RoleManager.isRoleHeld`
 * (29+) is the "official" path but resolveActivity is equivalent and
 * version-portable. Falls back to false on any error.
 *
 * Live observation: not implemented for v1 — the default-emit-once base
 * behaviour from `DefaultBrowserService` is enough. We could poll on
 * Activity `onResume` later (the Settings page is a separate Activity, so
 * any change always returns through `onResume`).
 *
 * Settings deep-link: `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` opens the global
 * Default Apps page. Some OEM Android skins (Samsung One UI, Xiaomi MIUI)
 * land on a slightly different page; we accept that — there's no consistent
 * deeper deep-link.
 */
class AndroidDefaultBrowserService(
    private val context: Context,
    private val ownPackageName: String = context.packageName,
    private val probeUrl: String = "http://example.com",
) : DefaultBrowserService {

    override suspend fun isDefaultBrowser(): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(probeUrl))
        val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolved?.activityInfo?.packageName == ownPackageName
    }.getOrDefault(false)

    override val canOpenSystemSettings: Boolean = true

    override suspend fun openSystemSettings(): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrElse { false }
}
