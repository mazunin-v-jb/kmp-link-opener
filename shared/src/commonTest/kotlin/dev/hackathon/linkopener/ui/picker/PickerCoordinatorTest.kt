package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.domain.RuleEngine
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
    fun pickerFollowsTheUserOrderConfiguredInSettings() = runTest {
        // Discovery returns browsers in the OS-walk order [safari, chrome,
        // firefox]; the user has drag-reordered them in Settings to
        // [firefox, chrome, safari]. The picker must show that exact order
        // — no exclusions, no rules in this scenario, so a regression here
        // points squarely at the order plumbing
        // (`PickerCoordinator` → `applyUserOrder` → `settings.browserOrder`).
        val settings = AppSettings(
            browserOrder = listOf(
                firefox.toBrowserId(),
                chrome.toBrowserId(),
                safari.toBrowserId(),
            ),
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
        assertEquals(listOf(firefox, chrome, safari), state.browsers)
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
        // Post-exclusion the available list is just [chrome2], so the
        // auto-launch short-circuit fires — assert that the launcher saw the
        // *non-excluded* installation rather than the picker showing it.
        val chrome2 = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome 2.app", "126")
        val settings = AppSettings(excludedBrowserIds = setOf(chrome.toBrowserId()))
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(chrome, chrome2),
            settings = settings,
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(listOf(chrome2 to "https://example.com"), launcher.calls)
    }

    @Test
    fun pickBrowserCallsLauncherAndReturnsToHidden() = runTest {
        val launcher = RecordingLauncher()
        // Two browsers so the picker actually shows — a one-browser list
        // would auto-launch and never reach the pickBrowser path.
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome),
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
        // Two browsers so the picker actually shows; a one-browser list would
        // auto-launch and there'd be nothing to dismiss.
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome),
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
        // Two browsers so the picker stays Showing across both URLs (a
        // one-browser list would auto-launch each one immediately).
        val coord = newCoordinator(scope = this, browsers = listOf(safari, chrome))

        coord.handleIncomingUrl("https://first.example")
        testScheduler.advanceUntilIdle()
        coord.handleIncomingUrl("https://second.example")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals("https://second.example", state.url)
    }

    @Test
    fun singleAvailableBrowserAutoLaunchesWithoutShowingPicker() = runTest {
        // No-choice short-circuit: one discovered browser, no exclusions —
        // the picker would be a single-row "tap me" dialog, so we skip it
        // and launch directly.
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(listOf(safari to "https://example.com"), launcher.calls)
    }

    @Test
    fun exclusionsReducingToOneBrowserAlsoAutoLaunches() = runTest {
        // Same short-circuit, just reached via exclusions instead of a thin
        // discovery list — two browsers installed, user excluded one, only
        // one survives → launch directly.
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome),
            settings = AppSettings(excludedBrowserIds = setOf(chrome.toBrowserId())),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(listOf(safari to "https://example.com"), launcher.calls)
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
    fun matchingRuleOpensDirectlyAndDoesNotShowPicker() = runTest {
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, firefox),
            settings = AppSettings(
                rules = listOf(
                    UrlRule(pattern = "*.example.com", browserId = firefox.toBrowserId()),
                ),
            ),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://foo.example.com/page")
        testScheduler.advanceUntilIdle()

        assertEquals(PickerState.Hidden, coord.state.value)
        assertEquals(listOf(firefox to "https://foo.example.com/page"), launcher.calls)
    }

    @Test
    fun ruleMatchingExcludedBrowserFallsThroughToPicker() = runTest {
        // Decision #4: exclusion wins. Rule points at Firefox but Firefox
        // is excluded; engine skips and the picker shows the remaining set.
        // Three browsers in the discovery list so post-exclusion still has
        // two — keeps the picker showing rather than tripping the auto-launch
        // short-circuit, so the assertion stays focused on the rule-vs-
        // exclusion arbitration this test is named for.
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome, firefox),
            settings = AppSettings(
                excludedBrowserIds = setOf(firefox.toBrowserId()),
                rules = listOf(
                    UrlRule(pattern = "*.example.com", browserId = firefox.toBrowserId()),
                ),
            ),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://foo.example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        // Picker shows the surviving pair (Firefox is excluded), launcher untouched.
        assertEquals(listOf(safari, chrome), state.browsers)
        assertEquals(emptyList(), launcher.calls)
    }

    @Test
    fun ruleNotMatchingFallsThroughToPicker() = runTest {
        val launcher = RecordingLauncher()
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, firefox),
            settings = AppSettings(
                rules = listOf(
                    UrlRule(pattern = "*.youtube.com", browserId = firefox.toBrowserId()),
                ),
            ),
            launcher = launcher,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals(listOf(safari, firefox), state.browsers)
        assertEquals(emptyList(), launcher.calls)
    }

    @Test
    fun discoveryFailureFallsBackToEmptyShowingState() = runTest {
        val coord = PickerCoordinator(
            discoverBrowsers = DiscoverBrowsersUseCase(ThrowingBrowserRepository()),
            getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository()),
            launcher = RecordingLauncher(),
            ruleEngine = RuleEngine(),
            scope = this,
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        val state = coord.state.value
        assertIs<PickerState.Showing>(state)
        assertEquals("https://example.com", state.url)
        assertEquals(emptyList(), state.browsers)
    }

    @Test
    fun discoveryFailureInvokesLogErrorCallback() = runTest {
        val recorded = mutableListOf<Pair<String, String?>>()
        val coord = PickerCoordinator(
            discoverBrowsers = DiscoverBrowsersUseCase(ThrowingBrowserRepository()),
            getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository()),
            launcher = RecordingLauncher(),
            ruleEngine = RuleEngine(),
            scope = this,
            logError = { tag, throwable -> recorded += tag to throwable.message },
        )

        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        // The default callback writes to stderr; the injected one captures
        // the call so we can assert both the tag (so devs can grep) and the
        // forwarded throwable.
        assertEquals(1, recorded.size)
        assertEquals("picker", recorded[0].first)
        assertEquals("discovery failed", recorded[0].second)
    }

    @Test
    fun successfulDiscoveryDoesNotInvokeLogError() = runTest {
        val recorded = mutableListOf<Pair<String, Throwable>>()
        // Two browsers so the sanity check at the end can assert the picker
        // actually showed — a one-browser list would auto-launch and leave
        // state at Hidden, defeating the "happy path completed" probe.
        val coord = newCoordinator(
            scope = this,
            browsers = listOf(safari, chrome),
        )
        // Sanity: handle a URL that goes through the happy path. This
        // coordinator was built without an injected logError, but we can
        // re-verify with one wired in by reaching for the explicit ctor.
        val coord2 = PickerCoordinator(
            discoverBrowsers = DiscoverBrowsersUseCase(StaticBrowserRepository(listOf(safari, chrome))),
            getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository()),
            launcher = RecordingLauncher(),
            ruleEngine = RuleEngine(),
            scope = this,
            logError = { tag, t -> recorded += tag to t },
        )

        coord2.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), recorded)
        // and the silent-original-coord still works
        coord.handleIncomingUrl("https://example.com")
        testScheduler.advanceUntilIdle()
        assertIs<PickerState.Showing>(coord.state.value)
    }

    private fun newCoordinator(
        scope: kotlinx.coroutines.test.TestScope,
        browsers: List<Browser> = emptyList(),
        settings: AppSettings = AppSettings.Default,
        launcher: LinkLauncher = RecordingLauncher(),
        ruleEngine: RuleEngine = RuleEngine(),
    ): PickerCoordinator = PickerCoordinator(
        discoverBrowsers = DiscoverBrowsersUseCase(StaticBrowserRepository(browsers)),
        getSettings = GetSettingsFlowUseCase(InMemorySettingsRepository(settings)),
        launcher = launcher,
        ruleEngine = ruleEngine,
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
        override suspend fun setRules(rules: List<dev.hackathon.linkopener.core.model.UrlRule>) {
            _settings.update { it.copy(rules = rules) }
        }
        override suspend fun setShowBrowserProfiles(enabled: Boolean) {
            _settings.update { it.copy(showBrowserProfiles = enabled) }
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
