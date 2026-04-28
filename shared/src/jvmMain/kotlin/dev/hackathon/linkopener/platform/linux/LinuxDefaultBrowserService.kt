package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.platform.DefaultBrowserService

class LinuxDefaultBrowserService : DefaultBrowserService {
    // No consistent settings UI to deep-link into across DEs (GNOME, KDE,
    // XFCE, etc.). UI shows text-only instructions for now; per-DE deep
    // links can be added in stage 8.
    override val canOpenSystemSettings: Boolean = false
    override suspend fun isDefaultBrowser(): Boolean = false
    override suspend fun openSystemSettings(): Boolean = false
}
