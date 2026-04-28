package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetCanOpenSystemSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.ObserveIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.OpenDefaultBrowserSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSettings: GetSettingsFlowUseCase,
    private val updateTheme: UpdateThemeUseCase,
    private val updateLanguage: UpdateLanguageUseCase,
    private val setAutoStart: SetAutoStartUseCase,
    private val setBrowserExcluded: SetBrowserExcludedUseCase,
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    observeIsDefaultBrowser: ObserveIsDefaultBrowserUseCase,
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

    private val _browsers = MutableStateFlow<BrowsersState>(BrowsersState.Loading)
    val browsers: StateFlow<BrowsersState> = _browsers.asStateFlow()

    // Live binding: macOS overrides DefaultBrowserService.observeIsDefaultBrowser
    // with a WatchService against the LaunchServices preferences plist, so when
    // the user picks a different default in System Settings (or any installer
    // changes the binding) the indicator flips without us polling. Other
    // platforms get a one-shot emission via the interface's default impl until
    // their service grows a real watcher.
    val isDefaultBrowser: StateFlow<Boolean> = observeIsDefaultBrowser()
        .catch { emit(false) }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    init {
        refreshBrowsers()
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

    fun refreshBrowsers() {
        scope.launch {
            _browsers.value = BrowsersState.Loading
            try {
                _browsers.value = BrowsersState.Loaded(discoverBrowsers())
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _browsers.value = BrowsersState.Error(t.message ?: "Browser discovery failed")
            }
        }
    }

    fun openSystemSettings() {
        scope.launch {
            openDefaultBrowserSettings()
        }
    }
}
