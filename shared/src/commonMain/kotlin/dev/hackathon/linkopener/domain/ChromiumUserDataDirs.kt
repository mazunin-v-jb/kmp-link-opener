package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.BrowserFamily

/**
 * Single source of truth for Chromium-family browser identification:
 * - Maps `bundleId` to its on-disk user-data directory (relative to
 *   `~/Library/Application Support/` on macOS).
 * - Drives [detectBrowserFamily] for runtime classification.
 *
 * Adding another Chromium-family browser = one entry here. No other code
 * change required.
 */

/**
 * Map from macOS bundleId to the user-data directory under
 * `~/Library/Application Support/`. Each entry is a known Chromium-family
 * browser whose profiles live in `<userDataPath>/Local State`.
 */
val chromiumUserDataPaths: Map<String, String> = mapOf(
    "com.google.Chrome"           to "Google/Chrome",
    "com.google.Chrome.beta"      to "Google/Chrome Beta",
    "com.google.Chrome.dev"       to "Google/Chrome Dev",
    "com.google.Chrome.canary"    to "Google/Chrome Canary",
    "com.microsoft.Edge"          to "Microsoft Edge",
    "com.microsoft.Edge.beta"     to "Microsoft Edge Beta",
    "com.microsoft.Edge.dev"      to "Microsoft Edge Dev",
    "com.brave.Browser"           to "BraveSoftware/Brave-Browser",
    "com.brave.Browser.beta"      to "BraveSoftware/Brave-Browser-Beta",
    "com.brave.Browser.nightly"   to "BraveSoftware/Brave-Browser-Nightly",
    "com.vivaldi.Vivaldi"         to "Vivaldi",
    "com.operasoftware.Opera"     to "com.operasoftware.Opera",
    "com.operasoftware.OperaGX"   to "com.operasoftware.OperaGX",
    "org.chromium.Chromium"       to "Chromium",
)

/**
 * Returns the runtime [BrowserFamily] of a browser identified by its bundleId.
 * Single point of truth — flip the rules here to change classification.
 */
fun detectBrowserFamily(bundleId: String): BrowserFamily = when {
    bundleId in chromiumUserDataPaths -> BrowserFamily.Chromium
    bundleId.startsWith("org.mozilla.firefox") -> BrowserFamily.Firefox
    bundleId.startsWith("com.apple.Safari") -> BrowserFamily.Safari
    else -> BrowserFamily.Other
}

/**
 * Windows-style family detection: bundle-id-by-string-key (registry
 * sub-key names like `Google Chrome`) doesn't fit the macOS reverse-DNS
 * format, so we keyword-match on display name. Used by
 * [dev.hackathon.linkopener.platform.windows.WindowsBrowserDiscovery].
 *
 * Conservative: anything we don't recognise → [BrowserFamily.Other],
 * which means the launcher won't add Chromium-specific flags. Adding a
 * new Chromium-family Windows browser = one keyword here.
 */
fun detectBrowserFamilyByDisplayName(displayName: String): BrowserFamily {
    val lower = displayName.lowercase()
    return when {
        "chrome" in lower ||
            "edge" in lower ||
            "brave" in lower ||
            "vivaldi" in lower ||
            "opera" in lower ||
            "chromium" in lower -> BrowserFamily.Chromium
        "firefox" in lower -> BrowserFamily.Firefox
        else -> BrowserFamily.Other
    }
}
