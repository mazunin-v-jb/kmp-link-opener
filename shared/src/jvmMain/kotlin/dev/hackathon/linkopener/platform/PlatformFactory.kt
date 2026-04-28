package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.platform.linux.LinuxDefaultBrowserService
import dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager
import dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery
import dev.hackathon.linkopener.platform.macos.MacOsDefaultBrowserService
import dev.hackathon.linkopener.platform.windows.WindowsDefaultBrowserService

object PlatformFactory {

    val currentOs: HostOs by lazy { detectHostOs() }

    fun createAutoStartManager(): AutoStartManager = when (currentOs) {
        HostOs.MacOs -> MacOsAutoStartManager()
        HostOs.Windows,
        HostOs.Linux,
        HostOs.Other -> NoOpAutoStartManager()
    }

    fun createBrowserDiscovery(): BrowserDiscovery = when (currentOs) {
        HostOs.MacOs -> MacOsBrowserDiscovery()
        HostOs.Windows,
        HostOs.Linux,
        HostOs.Other -> EmptyBrowserDiscovery(System.getProperty("os.name").orEmpty())
    }

    fun createUrlReceiver(): UrlReceiver = JvmUrlReceiver()

    fun createDefaultBrowserService(): DefaultBrowserService = when (currentOs) {
        HostOs.MacOs -> MacOsDefaultBrowserService()
        HostOs.Windows -> WindowsDefaultBrowserService()
        HostOs.Linux,
        HostOs.Other -> LinuxDefaultBrowserService()
    }

    // TODO: stage 3b — once MacOsLinkLauncher / WindowsLinkLauncher /
    //  LinuxLinkLauncher are written by the colleague (per
    //  ai_stages/042_browser_picker_popup/CONTRACT_for_link_launcher.md),
    //  switch to a when-block on currentOs. PrintingLinkLauncher stays as
    //  the HostOs.Other fallback.
    fun createLinkLauncher(): LinkLauncher = PrintingLinkLauncher()

    private fun detectHostOs(): HostOs =
        detectHostOs(System.getProperty("os.name").orEmpty())

    internal fun detectHostOs(osName: String): HostOs {
        val name = osName.lowercase()
        return when {
            "mac" in name || "darwin" in name -> HostOs.MacOs
            "win" in name -> HostOs.Windows
            "nux" in name || "nix" in name || "aix" in name -> HostOs.Linux
            else -> HostOs.Other
        }
    }
}
