package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.platform.macos.MacOsAutoStartManager
import dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery

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

    private fun detectHostOs(): HostOs {
        val name = System.getProperty("os.name")?.lowercase().orEmpty()
        return when {
            "mac" in name || "darwin" in name -> HostOs.MacOs
            "win" in name -> HostOs.Windows
            "nux" in name || "nix" in name || "aix" in name -> HostOs.Linux
            else -> HostOs.Other
        }
    }
}

enum class HostOs { MacOs, Windows, Linux, Other }
