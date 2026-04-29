package dev.hackathon.linkopener.platform.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.platform.BrowserDiscovery

/**
 * Enumerates installed apps that registered as `ACTION_VIEW http(s)` handlers.
 * On Android `PackageManager` is the single source of truth — there's no
 * registry / `.app` walk like on desktop, just one query.
 *
 * The probe URL (`http://example.com`) doesn't matter — Android resolves
 * intents by scheme + category, not by the host. We use a deterministic
 * URL so logs and tests are stable.
 *
 * Filters ourselves out by package name so we don't appear in our own
 * picker. Dedupes by package id (an installed browser only ever shows up
 * once even if the manifest declares multiple `<activity>` entries with
 * matching intent-filters).
 */
class AndroidBrowserDiscovery(
    private val context: Context,
    private val ownPackageName: String = context.packageName,
    // Probe URL injected for tests — production always uses the default.
    private val probeUrl: String = "http://example.com",
) : BrowserDiscovery {

    override suspend fun discover(): List<Browser> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(probeUrl))
        val resolveInfos: List<ResolveInfo> = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo?.packageName != ownPackageName }
            .distinctBy { it.activityInfo.packageName }
            .map { it.toBrowser(context.packageManager) }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }
}

private fun ResolveInfo.toBrowser(pm: PackageManager): Browser {
    val pkg = activityInfo.packageName
    val label = loadLabel(pm).toString().ifBlank { pkg }
    val versionName = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
    return Browser(
        // Android uses package name as the stable identity; reuse it for
        // `bundleId` (cross-OS we treat that field as "OS-stable id").
        bundleId = pkg,
        displayName = label,
        // applicationPath holds the package id on Android — the launcher
        // hands it to Intent.setPackage(...) directly. Mirrors how the
        // Windows impl carries an .exe path: opaque OS-specific token.
        applicationPath = pkg,
        version = versionName,
        // Profile / family are desktop-only concerns. Android Chrome
        // doesn't expose profiles via PackageManager; skip detection
        // for v1.
        profile = null,
        family = null,
    )
}
