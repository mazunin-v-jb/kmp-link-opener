package dev.hackathon.linkopener.platform.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.platform.LinkLauncher

/**
 * Launches a browser by sending an `ACTION_VIEW` intent targeted at its
 * package id. On Android `applicationPath` carries the package name (set
 * by `AndroidBrowserDiscovery`) — we feed it straight into
 * `Intent.setPackage(...)` so the system bypasses the chooser and routes
 * the URL to that one app.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is required because we're called from a
 * picker activity that's about to finish — without the flag the started
 * activity would inherit our (about-to-die) task and get torn down.
 *
 * Returns true on a successful `startActivity` call. We swallow
 * `ActivityNotFoundException` (target uninstalled mid-pick) and
 * `SecurityException` (target's intent-filter changed to require a
 * permission we don't hold) — both are user-recoverable.
 */
class AndroidLinkLauncher(
    private val context: Context,
) : LinkLauncher {

    override suspend fun openIn(browser: Browser, url: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setPackage(browser.applicationPath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }
}
