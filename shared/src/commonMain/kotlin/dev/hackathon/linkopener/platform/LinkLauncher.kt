package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

/**
 * Launches a browser with a given URL.
 *
 * Platform implementations live in `shared/jvmMain/platform/{macos,windows,linux}`.
 * This stage ships a [PrintingLinkLauncher] stub; the real per-OS launchers are
 * delivered by the colleague's stage 3b — see
 * `ai_stages/042_browser_picker_popup/CONTRACT_for_link_launcher.md`.
 */
interface LinkLauncher {
    /**
     * Open [url] in [browser]. Returns true if the browser process started
     * successfully. fire-and-forget — caller does not await the browser to
     * actually load the URL.
     */
    suspend fun openIn(browser: Browser, url: String): Boolean
}
