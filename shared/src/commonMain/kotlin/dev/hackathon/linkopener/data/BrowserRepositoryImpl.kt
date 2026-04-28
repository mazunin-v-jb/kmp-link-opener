package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BrowserRepositoryImpl(
    private val discovery: BrowserDiscovery,
    private val settings: SettingsRepository,
) : BrowserRepository {
    private val mutex = Mutex()
    private var cachedDiscovered: List<Browser>? = null

    override suspend fun getInstalledBrowsers(): List<Browser> = mutex.withLock {
        val discovered = cachedDiscovered ?: discovery.discover().also { cachedDiscovered = it }
        finalize(discovered)
    }

    override suspend fun refresh(): List<Browser> = mutex.withLock {
        val discovered = discovery.discover().also { cachedDiscovered = it }
        finalize(discovered)
    }

    private fun finalize(discovered: List<Browser>): List<Browser> =
        collapseProfilesIfDisabled(mergeWithManual(discovered))

    /**
     * Combines auto-discovered browsers with the user's manually-added ones.
     * If the same `applicationPath` shows up on both sides — i.e. the system
     * has caught up to a browser the user manually registered earlier — the
     * discovered version wins, on the assumption that auto-discovery has the
     * freshest metadata (e.g. version after an update).
     */
    private fun mergeWithManual(discovered: List<Browser>): List<Browser> {
        val manual = settings.settings.value.manualBrowsers
        if (manual.isEmpty()) return discovered
        val seen = discovered.mapTo(HashSet()) { it.applicationPath }
        val extras = manual.filterNot { it.applicationPath in seen }
        return discovered + extras
    }

    /**
     * Stage 047 toggle: when `showBrowserProfiles == false`, profile-expanded
     * Chromium rows (one per `Local State` profile) are folded back into a
     * single row per parent applicationPath. The fold drops the `profile`
     * field but preserves the rest of the parent metadata. Single point of
     * decision so flipping the policy or going per-browser later only
     * touches this method.
     */
    private fun collapseProfilesIfDisabled(list: List<Browser>): List<Browser> {
        if (settings.settings.value.showBrowserProfiles) return list
        // Group by applicationPath; if a group has any profile rows, emit one
        // representative without `profile`. Single-row groups pass through.
        val byPath = list.groupBy { it.applicationPath }
        return byPath.flatMap { (_, group) ->
            when {
                group.size == 1 -> group
                group.any { it.profile != null } -> listOf(group.first().copy(profile = null))
                else -> group
            }
        }
    }
}
