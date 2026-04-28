package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

/**
 * Logging stub — used by `PlatformFactory.createLinkLauncher()` for any OS
 * that doesn't have a real launcher yet. macOS already uses
 * `MacOsLinkLauncher`; Windows and Linux are still routed here pending stages
 * 7 and 8 (per-OS discovery there will populate `Browser.applicationPath` with
 * something exec'able). Also handy as a default in unit tests that don't care
 * about real process spawning.
 */
class PrintingLinkLauncher : LinkLauncher {
    override suspend fun openIn(browser: Browser, url: String): Boolean {
        println("[launch] would open $url in ${browser.displayName} (${browser.applicationPath})")
        return true
    }
}
