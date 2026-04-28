package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserRepositoryImplTest {

    @Test
    fun cachesDiscoveryResultBetweenCalls() = runTest {
        val discovery = CountingDiscovery(
            listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0")),
        )
        val repository = BrowserRepositoryImpl(discovery, FakeSettings())

        repeat(3) { repository.getInstalledBrowsers() }

        assertEquals(1, discovery.callCount)
    }

    @Test
    fun returnsSameListEachCall() = runTest {
        val list = listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"))
        val repository = BrowserRepositoryImpl(CountingDiscovery(list), FakeSettings())

        val first = repository.getInstalledBrowsers()
        val second = repository.getInstalledBrowsers()

        assertEquals(list, first)
        assertEquals(list, second)
    }

    @Test
    fun refreshBypassesCacheAndUpdatesIt() = runTest {
        val initial = listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"))
        val updated = initial + Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", "126")
        val discovery = SwitchingDiscovery(listOf(initial, updated))
        val repository = BrowserRepositoryImpl(discovery, FakeSettings())

        // Populate the cache.
        assertEquals(initial, repository.getInstalledBrowsers())
        // Force a fresh scan — must run discovery again and return the new list.
        assertEquals(updated, repository.refresh())
        // Cache now reflects the refreshed value, no further discovery calls.
        assertEquals(updated, repository.getInstalledBrowsers())
        assertEquals(2, discovery.callCount)
    }

    @Test
    fun mergesManualBrowsersAfterDiscovered() = runTest {
        val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0")
        val custom = Browser("com.example.custom", "Custom", "/Apps/Custom.app", "1.0")
        val repo = BrowserRepositoryImpl(
            CountingDiscovery(listOf(safari)),
            FakeSettings(initial = AppSettings(manualBrowsers = listOf(custom))),
        )

        val result = repo.getInstalledBrowsers()

        // Discovered first, manual appended at the tail.
        assertEquals(listOf(safari, custom), result)
    }

    @Test
    fun discoveredWinsOverManualOnPathConflict() = runTest {
        // User manually registered Safari before the system caught up;
        // discovery now finds it too. Discovered version wins (presumably has
        // fresher metadata like the real version string).
        val discoveredSafari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4")
        val manualSafari = Browser(
            bundleId = "com.apple.Safari",
            displayName = "Safari (manual)",
            applicationPath = "/Applications/Safari.app",
            version = null,
        )
        val repo = BrowserRepositoryImpl(
            CountingDiscovery(listOf(discoveredSafari)),
            FakeSettings(initial = AppSettings(manualBrowsers = listOf(manualSafari))),
        )

        val result = repo.getInstalledBrowsers()

        assertEquals(listOf(discoveredSafari), result)
    }

    @Test
    fun manualOnlyBrowserAppearsWhenDiscoveryEmpty() = runTest {
        val custom = Browser("com.example.custom", "Custom", "/Apps/Custom.app", "1.0")
        val repo = BrowserRepositoryImpl(
            CountingDiscovery(emptyList()),
            FakeSettings(initial = AppSettings(manualBrowsers = listOf(custom))),
        )

        val result = repo.getInstalledBrowsers()

        assertEquals(listOf(custom), result)
    }

    private class CountingDiscovery(private val value: List<Browser>) : BrowserDiscovery {
        var callCount = 0
            private set

        override suspend fun discover(): List<Browser> {
            callCount += 1
            return value
        }
    }

    private class SwitchingDiscovery(private val sequence: List<List<Browser>>) : BrowserDiscovery {
        var callCount = 0
            private set

        override suspend fun discover(): List<Browser> {
            val index = callCount.coerceAtMost(sequence.lastIndex)
            callCount += 1
            return sequence[index]
        }
    }

    private class FakeSettings(initial: AppSettings = AppSettings.Default) : SettingsRepository {
        private val _settings = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = _settings
        override suspend fun updateTheme(theme: AppTheme) = error("not used")
        override suspend fun updateLanguage(language: AppLanguage) = error("not used")
        override suspend fun setAutoStart(enabled: Boolean) = error("not used")
        override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) = error("not used")
        override suspend fun setBrowserOrder(order: List<BrowserId>) = error("not used")
        override suspend fun addManualBrowser(browser: Browser) = error("not used")
        override suspend fun removeManualBrowser(id: BrowserId) = error("not used")
        override suspend fun setRules(rules: List<dev.hackathon.linkopener.core.model.UrlRule>) = error("not used")
    }
}
