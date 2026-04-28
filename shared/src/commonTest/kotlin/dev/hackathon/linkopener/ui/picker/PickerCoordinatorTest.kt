package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest

class PickerCoordinatorTest {

    private val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4")
    private val chrome = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", "124")
    private val firefox = Browser("org.mozilla.firefox", "Firefox", "/Applications/Firefox.app", "125")

    @Test
    fun startsHidden() = runTest {
        val coord = newCoordinator(scope = this)
        assertEquals(PickerState.Hidden, coord.state.value)
    }

    @Test
    fun handleIncomingUrlMovesToShowingWithDiscoveredBrowsers() = runTest {
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome, firefox),
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals("https://example.com", state.url)
        assertEquals(listOf(safari, chrome, firefox), state.browsers)
    }

    @Test
    fun excludedBrowsersAreFilteredOut() = runTest {
        val settings = AppSettings(excludedBrowserIds = setOf(chrome.toBrowserId()))
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome, firefox),
            settings = settings,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals(listOf(safari, firefox), state.browsers)
    }

    @Test
    fun userOrderIsRespectedAfterExclusionFilter() = runTest {
        val settings = AppSettings(
            excludedBrowserIds = setOf(chrome.toBrowserId()),
            browserOrder = listOf(firefox.toBrowserId(), safari.toBrowserId()),
        )
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome, firefox),
            settings = settings,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        // Chrome filtered by exclusion, then user order pins Firefox before Safari.
        assertEquals(listOf(firefox, safari), state.browsers)
    }

    @Test
    fun excludingOneInstallationKeepsAnotherWithSameBundleId() = runTest {
        // Two installs of Chrome (parallel versions) share a bundleId but live
        // at different paths; excluding one must not exclude the other.
        val chrome2 = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome 2.app", "126")
        val settings = AppSettings(excludedBrowserIds = setOf(chrome.toBrowserId()))
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(chrome, chrome2),
            settings = settings,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals(listOf(chrome2), state.browsers)
    }

    @Test
    fun pickBrowserCallsLauncherAndReturnsToHidden() = runTest {
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        coord.pickBrowser(safari)
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(listOf(safari to "https://example.com"), launcher.calls)
    }

    @Test
    fun pickBrowserDoesNothingWhenHidden() = runTest {
        val launcher = RecordingLauncher()
        val coord = newCoordinator(scope = this, launcher = launcher)

        coord.pickBrowser(safari)
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(emptyList(), launcher.calls)
    }

    @Test
    fun dismissReturnsToHiddenWithoutLaunching() = runTest {
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        coord.dismiss()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(emptyList(), launcher.calls)
    }

    @Test
    fun secondUrlReplacesFirstWhileShowing() = runTest {
        val coord = newCoordinator(scope = this, browsers = listOf(safari))

        coord.handleIncomingUrl("https://first.example")
        testScheduler.advanceUntilIdle()
        coord.handleIncomingUrl("https://second.example")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals("https://second.example", state.url)
    }

    @Test
    fun emptyBrowserListStillShowsPickerWithEmptyState() = runTest {
        val coord = newCoordinator(scope = this, browsers = emptyList())

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals(emptyList(), state.browsers)
    }

    @Test
    fun discoveryFailureFallsBackToEmptyShowingState() = runTest {
        val coord = PickerCoordinator(
            discoverBrowsers = DiscoverBrowsersUseCase(ThrowingBrowserRepository()),
            getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository()),
            launcher = RecordingLauncher(),
            scope = this,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals("https://example.com", state.url)
        assertEquals(emptyList(), state.browsers)
    }

    private fun newCoordinator(
        scope: kotlinx.coroutines.test.TestScope,
        browsers: List<Browser> = emptyList(),
        settings: AppSettings = AppSettings.Default,
        launcher: LinkLauncher = RecordingLauncher(),
    ): PickerCoordinator = PickerCoordinator(
        discoverBrowsers = DiscoverBrowsersUseCase(StaticBrowserRepository(browsers)),
        getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository(settings)),
        launcher = launcher,
        scope = scope,
    )

    private class StaticBrowserRepository(private val browsers: List<Browser>) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = browsers
        override suspend fun refresh(): List<Browser> = browsers
    }

    private class ThrowingBrowserRepository : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = error("discovery failed")
        override suspend fun refresh(): List<Browser> = error("discovery failed")
    }

    private class InMemorySettingsRepository(initial: AppSettings = AppSettings.Default) : SettingsRepository {
        private val _settings = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = _settings
        override suspend fun updateTheme(theme: dev.hackathon.linkopener.core.model.AppTheme) {
            _settings.update { it.copy(theme = theme) }
        }
        override suspend fun updateLanguage(language: dev.hackathon.linkopener.core.model.AppLanguage) {
            _settings.update { it.copy(language = language) }
        }
        override suspend fun setAutoStart(enabled: Boolean) {
            _settings.update { it.copy(autoStartEnabled = enabled) }
        }
        override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) {
            _settings.update {
                it.copy(
                    excludedBrowserIds = if (excluded) it.excludedBrowserIds + id
                    else it.excludedBrowserIds - id,
                )
            }
        }
        override suspend fun setBrowserOrder(order: List<BrowserId>) {
            _settings.update { it.copy(browserOrder = order) }
        }
        override suspend fun addManualBrowser(browser: Browser) {
            _settings.update { it.copy(manualBrowsers = it.manualBrowsers + browser) }
        }
        override suspend fun removeManualBrowser(id: BrowserId) {
            _settings.update {
                it.copy(
                    manualBrowsers = it.manualBrowsers.filterNot { b ->
                        BrowserId(b.applicationPath) == id
                    },
                )
            }
        }
    }

    private class RecordingLauncher : LinkLauncher {
        val calls = mutableListOf<Pair<Browser, String>>()
        override suspend fun openIn(browser: Browser, url: String): Boolean {
            calls += browser to url
            return true
        }
    }
}
