package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    getSettings: GetSettingsFlowUseCase,
    private val updateTheme: UpdateThemeUseCase,
    private val updateLanguage: UpdateLanguageUseCase,
    private val setAutoStart: SetAutoStartUseCase,
    private val setBrowserExcluded: SetBrowserExcludedUseCase,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = getSettings()

    fun onThemeSelected(theme: AppTheme) {
        scope.launch { updateTheme(theme) }
    }

    fun onLanguageSelected(language: AppLanguage) {
        scope.launch { updateLanguage(language) }
    }

    fun onAutoStartChanged(enabled: Boolean) {
        scope.launch { setAutoStart(enabled) }
    }

    fun onBrowserExclusionToggled(id: BrowserId, excluded: Boolean) {
        scope.launch { setBrowserExcluded(id, excluded) }
    }
}
