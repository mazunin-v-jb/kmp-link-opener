package dev.hackathon.linkopener.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DefaultBrowserService {
    suspend fun isDefaultBrowser(): Boolean

    /**
     * Emits `isDefaultBrowser()` whenever the underlying OS state changes
     * (the user picked a different default in System Settings, an installer
     * registered a new handler, etc.).
     *
     * The default implementation just emits the current value once and
     * completes — fine for non-mac platforms where we don't track changes
     * yet. macOS overrides this with a WatchService against the LaunchServices
     * preferences plist so the Settings UI flips the moment the user
     * switches default browser elsewhere.
     */
    fun observeIsDefaultBrowser(): Flow<Boolean> = flow { emit(isDefaultBrowser()) }

    /**
     * Whether [openSystemSettings] is expected to do anything on the current
     * platform. Linux returns false because there is no consistent settings
     * UI to deep-link into.
     */
    val canOpenSystemSettings: Boolean

    /** Returns true if the OS settings UI was launched successfully. */
    suspend fun openSystemSettings(): Boolean
}
