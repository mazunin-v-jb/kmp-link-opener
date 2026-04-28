package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.domain.applyUserOrder
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetCanOpenSystemSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.GetIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.ObserveIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.OpenDefaultBrowserSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserOrderUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSettings: GetSettingsFlowUseCase,
    private val updateTheme: UpdateThemeUseCase,
    private val updateLanguage: UpdateLanguageUseCase,
    private val setAutoStart: SetAutoStartUseCase,
    private val setBrowserExcluded: SetBrowserExcludedUseCase,
    private val setBrowserOrder: SetBrowserOrderUseCase,
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    observeIsDefaultBrowser: ObserveIsDefaultBrowserUseCase,
    private val getIsDefaultBrowser: GetIsDefaultBrowserUseCase,
    private val openDefaultBrowserSettings: OpenDefaultBrowserSettingsUseCase,
    getCanOpenSystemSettings: GetCanOpenSystemSettingsUseCase,
    private val scope: CoroutineScope,
    // Synchronously flips JVM Locale.getDefault on user language change so any
    // Compose recomposition (in any composition — main or Window
    // subcomposition) reads the new locale on its next pass. Defaults to
    // no-op so commonTest VMs don't need to know about JVM specifics.
    private val applyLocale: (AppLanguage) -> Unit = {},
) {
    val settings: StateFlow<AppSettings> = getSettings()

    val canOpenSystemSettings: Boolean = getCanOpenSystemSettings()

    // _browsers carries the list as the user sees it: ordering is applied at
    // load time and again whenever the user reorders. Avoids a long-lived
    // combine/stateIn coroutine, which keeps `runTest` from completing.
    private val _browsers = MutableStateFlow<BrowsersState>(BrowsersState.Loading)
    val browsers: StateFlow<BrowsersState> = _browsers.asStateFlow()

    private val _isDefaultBrowser = MutableStateFlow(false)

    // Live binding: macOS overrides DefaultBrowserService.observeIsDefaultBrowser
    // with a WatchService against the LaunchServices preferences plist, so when
    // the user picks a different default in System Settings (or any installer
    // changes the binding) the indicator flips without us polling. Other
    // platforms get a one-shot emission via the interface's default impl until
    // their service grows a real watcher — `refresh()` is the manual escape
    // hatch that re-reads the value on demand for those platforms.
    val isDefaultBrowser: StateFlow<Boolean> = _isDefaultBrowser.asStateFlow()

    init {
        scope.launch {
            observeIsDefaultBrowser()
                .catch { emit(false) }
                .collect { _isDefaultBrowser.value = it }
        }
        // Initial load reads from BrowserRepository's cache to keep Settings
        // open instantly — AppContainer's startup warm-up already paid the
        // discovery cost. Manual refresh() (button / retry) forces re-scan.
        loadBrowsers(forceRefresh = false)
        scope.launch {
            try {
                _isDefaultBrowser.value = getIsDefaultBrowser()
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // Initial read failure: leave default false; the observer
                // flow will populate later if/when it emits.
            }
        }
    }

    fun onThemeSelected(theme: AppTheme) {
        scope.launch { updateTheme(theme) }
    }

    fun onLanguageSelected(language: AppLanguage) {
        // Apply BEFORE launching the suspend so the JVM default flips on the
        // click thread, before the StateFlow emit fans out to any Compose
        // collector. Otherwise the main composition (TrayHost) and the Window
        // subcomposition (SettingsScreen) race — and on the *first* change
        // the subcomposition wins, leaving the smart-skipped header / sidebar
        // / banner translated to the previous locale.
        applyLocale(language)
        scope.launch { updateLanguage(language) }
    }

    fun onAutoStartChanged(enabled: Boolean) {
        scope.launch { setAutoStart(enabled) }
    }

    fun onBrowserExclusionToggled(id: BrowserId, excluded: Boolean) {
        scope.launch { setBrowserExcluded(id, excluded) }
    }

    fun onMoveBrowserUp(id: BrowserId) = reorder(id, -1)

    fun onMoveBrowserDown(id: BrowserId) = reorder(id, +1)

    private fun reorder(id: BrowserId, delta: Int) {
        val state = _browsers.value as? BrowsersState.Loaded ?: return
        val list = state.browsers
        val from = list.indexOfFirst { it.toBrowserId() == id }
        if (from < 0) return
        val to = from + delta
        if (to < 0 || to >= list.size) return
        val swapped = list.toMutableList().apply {
            val tmp = this[from]; this[from] = this[to]; this[to] = tmp
        }
        // Update the visible list eagerly so the row moves on click without
        // waiting for the persistence round-trip; persist the new order in the
        // background.
        _browsers.value = BrowsersState.Loaded(swapped)
        scope.launch { setBrowserOrder(swapped.map { it.toBrowserId() }) }
    }

    /**
     * User-triggered re-scan of installed browsers (refresh / retry buttons).
     * Bypasses the repository cache so a browser installed while Settings
     * was open shows up on the next click.
     */
    fun refreshBrowsers() {
        loadBrowsers(forceRefresh = true)
    }

    private fun loadBrowsers(forceRefresh: Boolean) {
        scope.launch {
            _browsers.value = BrowsersState.Loading
            try {
                val raw = discoverBrowsers(forceRefresh = forceRefresh)
                _browsers.value = BrowsersState.Loaded(
                    applyUserOrder(raw, settings.value.browserOrder),
                )
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _browsers.value = BrowsersState.Error(t.message ?: "Browser discovery failed")
            }
        }
    }

    /**
     * User-triggered refresh from the Settings header. Re-runs browser
     * discovery (forced) and re-reads the default-browser status — useful on
     * platforms where [ObserveIsDefaultBrowserUseCase] is one-shot, or as a
     * manual escape hatch when the WatchService missed an event.
     */
    fun refresh() {
        refreshBrowsers()
        scope.launch {
            try {
                _isDefaultBrowser.value = getIsDefaultBrowser()
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                // Keep the previous value on read failure rather than flipping
                // to false — a transient read error shouldn't visually claim
                // we're no longer the default.
            }
        }
    }

    fun openSystemSettings() {
        scope.launch {
            openDefaultBrowserSettings()
        }
    }
}
