package dev.hackathon.linkopener.platform

interface DefaultBrowserService {
    suspend fun isDefaultBrowser(): Boolean

    /**
     * Whether [openSystemSettings] is expected to do anything on the current
     * platform. Linux returns false because there is no consistent settings
     * UI to deep-link into.
     */
    val canOpenSystemSettings: Boolean

    /** Returns true if the OS settings UI was launched successfully. */
    suspend fun openSystemSettings(): Boolean
}
