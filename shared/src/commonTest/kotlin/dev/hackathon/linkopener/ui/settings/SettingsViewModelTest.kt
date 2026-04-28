package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetCanOpenSystemSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.GetIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.ObserveIsDefaultBrowserUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import dev.hackathon.linkopener.domain.usecase.OpenDefaultBrowserSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserOrderUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SettingsViewModelTest {

    @Test
    fun forwardsSettingsFromUseCase() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)

        repo.emit(AppSettings(theme = AppTheme.Dark))

        assertEquals(AppTheme.Dark, vm.settings.value.theme)
    }

    @Test
    fun onThemeSelectedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)

        vm.onThemeSelected(AppTheme.Light)
        testScheduler.advanceUntilIdle()

        assertEquals(AppTheme.Light, repo.lastTheme)
    }

    @Test
    fun onLanguageSelectedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)

        vm.onLanguageSelected(AppLanguage.En)
        testScheduler.advanceUntilIdle()

        assertEquals(AppLanguage.En, repo.lastLanguage)
    }

    @Test
    fun onAutoStartChangedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)

        vm.onAutoStartChanged(true)
        testScheduler.advanceUntilIdle()

        assertEquals(true, repo.lastAutoStart)
    }

    @Test
    fun browsersStateMovesFromLoadingToLoaded() = runTest {
        val repo = FakeSettingsRepository()
        val browsers = listOf(
            Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4"),
        )
        val vm = newViewModel(
            repo = repo,
            browsers = browsers,
            scope = this,
        )

        testScheduler.advanceUntilIdle()

        val state = vm.browsers.value
        assertIs<BrowsersState.Loaded>(state)
        assertEquals(browsers, state.browsers)
    }

    @Test
    fun browsersStateGoesToErrorOnDiscoveryFailure() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(
            repo = repo,
            browserRepository = ThrowingBrowserRepository("boom"),
            scope = this,
        )

        testScheduler.advanceUntilIdle()

        val state = vm.browsers.value
        assertIs<BrowsersState.Error>(state)
        assertEquals("boom", state.message)
    }

    @Test
    fun isDefaultBrowserReflectsServiceAnswer() = runTest {
        val service = FakeDefaultBrowserService(isDefault = true)
        val vm = newViewModel(
            repo = FakeSettingsRepository(),
            defaultBrowserService = service,
            scope = this,
        )

        testScheduler.advanceUntilIdle()

        assertEquals(true, vm.isDefaultBrowser.value)
    }

    @Test
    fun isDefaultBrowserPicksUpServiceFlowEmissions() = runTest {
        // The live observer is a never-completing StateFlow, so we run the
        // ViewModel on an isolated scope that we cancel at the end. Without
        // this isolation, `runTest` would wait forever on the collect{} that
        // the ViewModel's stateIn(...) keeps alive.
        val vmScope = CoroutineScope(coroutineContext + Job())
        try {
            val service = ControllableDefaultBrowserService(initial = false)
            val vm = SettingsViewModel(
                getSettings = GetSettingsFlowUseCase(FakeSettingsRepository()),
                updateTheme = UpdateThemeUseCase(FakeSettingsRepository()),
                updateLanguage = UpdateLanguageUseCase(FakeSettingsRepository()),
                setAutoStart = SetAutoStartUseCase(FakeSettingsRepository()),
                setBrowserExcluded = SetBrowserExcludedUseCase(FakeSettingsRepository()),
                setBrowserOrder = SetBrowserOrderUseCase(FakeSettingsRepository()),
                discoverBrowsers = DiscoverBrowsersUseCase(StaticBrowserRepository(emptyList())),
                observeIsDefaultBrowser = ObserveIsDefaultBrowserUseCase(service),
                getIsDefaultBrowser = GetIsDefaultBrowserUseCase(service),
                openDefaultBrowserSettings = OpenDefaultBrowserSettingsUseCase(service),
                getCanOpenSystemSettings = GetCanOpenSystemSettingsUseCase(service),
                scope = vmScope,
            )

            testScheduler.advanceUntilIdle()
            assertEquals(false, vm.isDefaultBrowser.value)

            service.emit(true)
            testScheduler.advanceUntilIdle()
            assertEquals(true, vm.isDefaultBrowser.value)

            service.emit(false)
            testScheduler.advanceUntilIdle()
            assertEquals(false, vm.isDefaultBrowser.value)
        } finally {
            vmScope.cancel()
        }
    }

    @Test
    fun refreshRereadsBrowsersAndDefaultBrowserStatus() = runTest {
        val service = MutableDefaultBrowserService(initialIsDefault = false)
        val browserRepo = MutableBrowserRepository(initial = emptyList())
        val vm = newViewModel(
            repo = FakeSettingsRepository(),
            browserRepository = browserRepo,
            defaultBrowserService = service,
            scope = this,
        )
        testScheduler.advanceUntilIdle()
        assertEquals(false, vm.isDefaultBrowser.value)
        assertEquals(emptyList(), (vm.browsers.value as BrowsersState.Loaded).browsers)

        // Simulate state changing externally — e.g. the user picked a new
        // default browser and installed an extra browser while Settings was
        // open. Without manual refresh the one-shot observer flow wouldn't
        // notice (no live watcher in the fake).
        service.isDefault = true
        browserRepo.browsers = listOf(
            Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4"),
        )

        vm.refresh()
        testScheduler.advanceUntilIdle()

        assertEquals(true, vm.isDefaultBrowser.value)
        assertEquals(
            listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4")),
            (vm.browsers.value as BrowsersState.Loaded).browsers,
        )
    }

    @Test
    fun openSystemSettingsDelegatesToService() = runTest {
        val service = FakeDefaultBrowserService()
        val vm = newViewModel(
            repo = FakeSettingsRepository(),
            defaultBrowserService = service,
            scope = this,
        )

        vm.openSystemSettings()
        testScheduler.advanceUntilIdle()

        assertEquals(1, service.openCount)
    }

    @Test
    fun canOpenSystemSettingsForwardsServiceFlag() = runTest {
        val service = FakeDefaultBrowserService(canOpen = false)
        val vm = newViewModel(
            repo = FakeSettingsRepository(),
            defaultBrowserService = service,
            scope = this,
        )

        assertTrue(!vm.canOpenSystemSettings)
    }

    @Test
    fun onBrowserExclusionToggledExcludesBrowser() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)
        val id = BrowserId("com.apple.Safari")

        vm.onBrowserExclusionToggled(id, excluded = true)
        testScheduler.advanceUntilIdle()

        assertEquals(setOf(id), vm.settings.value.excludedBrowserIds)
    }

    @Test
    fun onMoveBrowserDownSwapsWithNeighbour() = runTest {
        val repo = FakeSettingsRepository()
        val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", null)
        val chrome = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", null)
        val firefox = Browser("org.mozilla.firefox", "Firefox", "/Applications/Firefox.app", null)
        val vm = newViewModel(
            repo = repo,
            browsers = listOf(safari, chrome, firefox),
            scope = this,
        )
        testScheduler.advanceUntilIdle()

        vm.onMoveBrowserDown(BrowserId("/Applications/Safari.app"))
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                BrowserId("/Applications/Chrome.app"),
                BrowserId("/Applications/Safari.app"),
                BrowserId("/Applications/Firefox.app"),
            ),
            vm.settings.value.browserOrder,
        )
    }

    @Test
    fun onMoveBrowserUpAtFirstPositionIsNoOp() = runTest {
        val repo = FakeSettingsRepository()
        val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", null)
        val chrome = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", null)
        val vm = newViewModel(
            repo = repo,
            browsers = listOf(safari, chrome),
            scope = this,
        )
        testScheduler.advanceUntilIdle()

        vm.onMoveBrowserUp(BrowserId("/Applications/Safari.app"))
        testScheduler.advanceUntilIdle()

        // No persisted order change; stays at default empty.
        assertEquals(emptyList(), vm.settings.value.browserOrder)
    }

    @Test
    fun onBrowserExclusionToggledRemovesBrowser() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo = repo, scope = this)
        val id = BrowserId("com.google.Chrome")
        vm.onBrowserExclusionToggled(id, excluded = true)
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(id), vm.settings.value.excludedBrowserIds)

        vm.onBrowserExclusionToggled(id, excluded = false)
        testScheduler.advanceUntilIdle()

        assertEquals(emptySet(), vm.settings.value.excludedBrowserIds)
    }

    private fun newViewModel(
        repo: SettingsRepository,
        scope: TestScope,
        browsers: List<Browser> = emptyList(),
        browserRepository: BrowserRepository = StaticBrowserRepository(browsers),
        defaultBrowserService: DefaultBrowserService = FakeDefaultBrowserService(),
    ): SettingsViewModel = SettingsViewModel(
        getSettings = GetSettingsFlowUseCase(repo),
        updateTheme = UpdateThemeUseCase(repo),
        updateLanguage = UpdateLanguageUseCase(repo),
        setAutoStart = SetAutoStartUseCase(repo),
        setBrowserExcluded = SetBrowserExcludedUseCase(repo),
        setBrowserOrder = SetBrowserOrderUseCase(repo),
        discoverBrowsers = DiscoverBrowsersUseCase(browserRepository),
        observeIsDefaultBrowser = ObserveIsDefaultBrowserUseCase(defaultBrowserService),
        getIsDefaultBrowser = GetIsDefaultBrowserUseCase(defaultBrowserService),
        openDefaultBrowserSettings = OpenDefaultBrowserSettingsUseCase(defaultBrowserService),
        getCanOpenSystemSettings = GetCanOpenSystemSettingsUseCase(defaultBrowserService),
        scope = scope,
    )

    private class FakeSettingsRepository : SettingsRepository {
        private val _settings = MutableStateFlow(AppSettings.Default)
        override val settings: StateFlow<AppSettings> = _settings

        var lastTheme: AppTheme? = null
        var lastLanguage: AppLanguage? = null
        var lastAutoStart: Boolean? = null

        fun emit(value: AppSettings) {
            _settings.value = value
        }

        override suspend fun updateTheme(theme: AppTheme) {
            lastTheme = theme
            _settings.update { it.copy(theme = theme) }
        }

        override suspend fun updateLanguage(language: AppLanguage) {
            lastLanguage = language
            _settings.update { it.copy(language = language) }
        }

        override suspend fun setAutoStart(enabled: Boolean) {
            lastAutoStart = enabled
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
    }

    private class StaticBrowserRepository(private val browsers: List<Browser>) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = browsers
        override suspend fun refresh(): List<Browser> = browsers
    }

    private class MutableBrowserRepository(initial: List<Browser>) : BrowserRepository {
        var browsers: List<Browser> = initial
        override suspend fun getInstalledBrowsers(): List<Browser> = browsers
        override suspend fun refresh(): List<Browser> = browsers
    }

    private class MutableDefaultBrowserService(
        initialIsDefault: Boolean = false,
    ) : DefaultBrowserService {
        var isDefault: Boolean = initialIsDefault
        override val canOpenSystemSettings: Boolean = true
        override suspend fun isDefaultBrowser(): Boolean = isDefault
        override suspend fun openSystemSettings(): Boolean = true
    }

    private class ThrowingBrowserRepository(private val message: String) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = error(message)
        override suspend fun refresh(): List<Browser> = error(message)
    }

    private class FakeDefaultBrowserService(
        private val isDefault: Boolean = false,
        private val canOpen: Boolean = true,
    ) : DefaultBrowserService {
        var openCount: Int = 0
        override val canOpenSystemSettings: Boolean = canOpen
        override suspend fun isDefaultBrowser(): Boolean = isDefault
        override suspend fun openSystemSettings(): Boolean {
            openCount += 1
            return true
        }
    }

    /**
     * Test double that lets the test push new "is default" values through the
     * `observeIsDefaultBrowser` flow at will, mimicking the live updates the
     * macOS WatchService produces in production.
     */
    private class ControllableDefaultBrowserService(initial: Boolean = false) : DefaultBrowserService {
        private val state = MutableStateFlow(initial)
        override val canOpenSystemSettings: Boolean = true
        override suspend fun isDefaultBrowser(): Boolean = state.value
        override fun observeIsDefaultBrowser(): kotlinx.coroutines.flow.Flow<Boolean> = state
        override suspend fun openSystemSettings(): Boolean = true
        fun emit(value: Boolean) { state.value = value }
    }
}
