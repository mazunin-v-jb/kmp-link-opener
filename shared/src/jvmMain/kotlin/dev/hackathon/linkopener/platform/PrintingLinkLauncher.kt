package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

/**
 * Stub implementation that just logs what would happen. Used until the
 * colleague's stage 3b delivers MacOsLinkLauncher / WindowsLinkLauncher /
 * LinuxLinkLauncher. Keeping this around as a fallback for HostOs.Other and
 * for unit tests is fine.
 */
class PrintingLinkLauncher : LinkLauncher {
    override suspend fun openIn(browser: Browser, url: String): Boolean {
        // TODO: replace with the per-OS launcher in PlatformFactory once
        //  stage 3b lands. See ai_stages/042_browser_picker_popup/
        //  CONTRACT_for_link_launcher.md for the spec.
        println("[launch] would open $url in ${browser.displayName} (${browser.applicationPath})")
        return true
    }
}
