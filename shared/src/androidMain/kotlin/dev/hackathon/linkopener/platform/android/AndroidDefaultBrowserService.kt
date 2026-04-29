package dev.hackathon.linkopener.platform.android

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
 * "Make default" prompt: on Android 10+ (API 29+) we use `RoleManager.
 * createRequestRoleIntent(ROLE_BROWSER)` which surfaces a system dialog
 * "Use Link Opener as default browser? Yes / No" — much better UX than
 * dropping the user into the Settings tree. Pre-29 Android falls back to
 * `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (still better than a generic
 * settings root).
 *
 * Some OEM Android skins (Samsung One UI, Xiaomi MIUI) land on a slightly
 * different settings page when the role-request flow isn't supported; we
 * accept that — there's no consistent deeper deep-link below the
 * RoleManager API.
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
        val intent = roleRequestIntent() ?: defaultAppsSettingsIntent()
        context.startActivity(intent)
        true
    }.getOrElse { false }

    /**
     * Returns an in-app role-request intent on API 29+ when the
     * `ROLE_BROWSER` role is available, otherwise null. The role might be
     * unavailable on locked-down builds (work profile, certain OEM skins);
     * caller falls back to the generic settings page.
     */
    private fun roleRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) return null
        return roleManager
            .createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun defaultAppsSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
