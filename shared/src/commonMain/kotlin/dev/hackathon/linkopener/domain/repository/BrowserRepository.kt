package dev.hackathon.linkopener.domain.repository

import dev.hackathon.linkopener.core.model.Browser

interface BrowserRepository {
    /**
     * Returns the cached browser list (or runs discovery once if no cache).
     * Use this on hot paths like the picker where startup latency matters.
     */
    suspend fun getInstalledBrowsers(): List<Browser>

    /**
     * Force re-discovery, bypassing any in-memory cache. Use for explicit
     * user-triggered "refresh" actions in Settings — picks up newly
     * installed browsers without restarting the app.
     */
    suspend fun refresh(): List<Browser>
}
