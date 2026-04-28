package dev.hackathon.linkopener.app

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
import dev.hackathon.linkopener.data.SettingsRepositoryImpl
import dev.hackathon.linkopener.domain.repository.AppInfoRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import dev.hackathon.linkopener.platform.AutoStartManager
import dev.hackathon.linkopener.platform.PlatformFactory
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

class AppContainer {

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val json: Json = Json { ignoreUnknownKeys = true }

    // Uses java.util.prefs under the hood on JVM via multiplatform-settings-no-arg.
    // Preferences land in the user root node — namespacing by app keys
    // (settings.theme, settings.language, ...) keeps them from colliding with
    // anything else the JVM might write into prefs.
    private val settingsStore: Settings = Settings()

    private val autoStartManager: AutoStartManager = PlatformFactory.createAutoStartManager()

    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        store = settingsStore,
        json = json,
        autoStartManager = autoStartManager,
    )

    val getAppInfoUseCase: GetAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)
    val getSettingsFlowUseCase: GetSettingsFlowUseCase = GetSettingsFlowUseCase(settingsRepository)
    val updateThemeUseCase: UpdateThemeUseCase = UpdateThemeUseCase(settingsRepository)
    val updateLanguageUseCase: UpdateLanguageUseCase = UpdateLanguageUseCase(settingsRepository)
    val setAutoStartUseCase: SetAutoStartUseCase = SetAutoStartUseCase(settingsRepository)
    val setBrowserExcludedUseCase: SetBrowserExcludedUseCase =
        SetBrowserExcludedUseCase(settingsRepository)

    fun newSettingsViewModel(): SettingsViewModel = SettingsViewModel(
        getSettings = getSettingsFlowUseCase,
        updateTheme = updateThemeUseCase,
        updateLanguage = updateLanguageUseCase,
        setAutoStart = setAutoStartUseCase,
        scope = coroutineScope,
    )
}
