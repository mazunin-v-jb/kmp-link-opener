package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SettingsViewModelTest {

    @Test
    fun forwardsSettingsFromUseCase() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo, this)

        repo.emit(AppSettings(theme = AppTheme.Dark))

        assertEquals(AppTheme.Dark, vm.settings.value.theme)
    }

    @Test
    fun onThemeSelectedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo, this)

        vm.onThemeSelected(AppTheme.Light)
        testScheduler.advanceUntilIdle()

        assertEquals(AppTheme.Light, repo.lastTheme)
    }

    @Test
    fun onLanguageSelectedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo, this)

        vm.onLanguageSelected(AppLanguage.En)
        testScheduler.advanceUntilIdle()

        assertEquals(AppLanguage.En, repo.lastLanguage)
    }

    @Test
    fun onAutoStartChangedDelegatesToRepository() = runTest {
        val repo = FakeSettingsRepository()
        val vm = newViewModel(repo, this)

        vm.onAutoStartChanged(true)
        testScheduler.advanceUntilIdle()

        assertEquals(true, repo.lastAutoStart)
    }

    private fun newViewModel(repo: SettingsRepository, scope: TestScope) = SettingsViewModel(
        getSettings = GetSettingsFlowUseCase(repo),
        updateTheme = UpdateThemeUseCase(repo),
        updateLanguage = UpdateLanguageUseCase(repo),
        setAutoStart = SetAutoStartUseCase(repo),
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
    }
}
