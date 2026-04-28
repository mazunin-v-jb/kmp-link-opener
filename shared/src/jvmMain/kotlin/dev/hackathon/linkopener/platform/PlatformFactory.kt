package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.platform.linux.LinuxDefaultBrowserService
import dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager
import dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery
import dev.hackathon.linkopener.platform.macos.MacOsBrowserMetadataExtractor
import dev.hackathon.linkopener.platform.macos.MacOsDefaultBrowserService
import dev.hackathon.linkopener.platform.macos.MacOsLinkLauncher
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

    fun createDefaultBrowserService(ownBundleId: String): DefaultBrowserService = when (currentOs) {
        HostOs.MacOs -> MacOsDefaultBrowserService(ownBundleId = ownBundleId)
        HostOs.Windows -> WindowsDefaultBrowserService()
        HostOs.Linux,
        HostOs.Other -> LinuxDefaultBrowserService()
    }

    fun createLinkLauncher(): LinkLauncher = when (currentOs) {
        HostOs.MacOs -> MacOsLinkLauncher()
        // Windows / Linux launchers are stages 7 / 8 — they need a real
        // applicationPath from per-OS discovery first. Until then, fall back
        // to printing so the picker UI still works end-to-end in dev.
        HostOs.Windows,
        HostOs.Linux,
        HostOs.Other -> PrintingLinkLauncher()
    }

    fun createBrowserMetadataExtractor(): BrowserMetadataExtractor = when (currentOs) {
        HostOs.MacOs -> MacOsBrowserMetadataExtractor()
        HostOs.Windows,
        HostOs.Linux,
        HostOs.Other -> UnsupportedManualBrowserExtractor()
    }

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
