package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.platform.linux.LinuxBrowserIconLoader
import dev.hackathon.linkopener.platform.linux.LinuxDefaultBrowserService
import dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager
import dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery
import dev.hackathon.linkopener.platform.macos.MacOsBrowserIconLoader
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

    fun createBrowserIconLoader(): BrowserIconLoader = when (currentOs) {
        // macOS treats `.app` bundles as opaque packages on disk, so
        // `FileSystemView.getSystemIcon` would return the generic Finder
        // folder icon. Going through the bundle's own resources
        // (`Info.plist` → `CFBundleIconFile` → `.icns` → sips) lands the
        // real browser icon every time.
        HostOs.MacOs -> MacOsBrowserIconLoader()
        // Windows .exe files embed icons as PE resources and Shell32 hands
        // them back through Swing's FileSystemView without bundle-package
        // weirdness, so the same generic FileSystemView path works there.
        HostOs.Windows -> FileSystemViewBrowserIconLoader()
        // Linux's FileSystemView is generic mimetype-only, so use the proper
        // .desktop + XDG icon-theme walker. Stage 8 (Linux discovery) will
        // hand us the right `applicationPath` (the .desktop file) — until
        // then this just sits ready.
        HostOs.Linux -> LinuxBrowserIconLoader()
        HostOs.Other -> NoOpBrowserIconLoader()
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
