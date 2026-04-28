package dev.hackathon.linkopener.app

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
import dev.hackathon.linkopener.data.BrowserRepositoryImpl
import dev.hackathon.linkopener.data.SettingsRepositoryImpl
import dev.hackathon.linkopener.domain.repository.AppInfoRepository
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCase
import dev.hackathon.linkopener.domain.usecase.GetCanOpenSystemSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.IsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.OpenDefaultBrowserSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import dev.hackathon.linkopener.platform.AutoStartManager
import dev.hackathon.linkopener.platform.BrowserDiscovery
import dev.hackathon.linkopener.platform.DefaultBrowserService
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.platform.LinkLauncher
import dev.hackathon.linkopener.platform.PlatformFactory
import dev.hackathon.linkopener.platform.UrlReceiver
import dev.hackathon.linkopener.ui.picker.PickerCoordinator
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppContainer {

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val currentOs: HostOs = PlatformFactory.currentOs

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val settingsStore: Settings = Settings()

    // Must match nativeDistributions.macOS.bundleID in desktopApp/build.gradle.kts.
    // Used both to exclude ourselves from the discovered-browsers list (so picking
    // "Link Opener" as the handler can't loop URLs back into our OpenURIHandler)
    // and to recognise ourselves when reading the system's default-browser binding.
    private val ownBundleId = "dev.hackathon.linkopener"

    private val autoStartManager: AutoStartManager = PlatformFactory.createAutoStartManager()
    private val browserDiscovery: BrowserDiscovery = PlatformFactory.createBrowserDiscovery()
    val urlReceiver: UrlReceiver = PlatformFactory.createUrlReceiver()
    private val defaultBrowserService: DefaultBrowserService =
        PlatformFactory.createDefaultBrowserService(ownBundleId = ownBundleId)
    private val linkLauncher: LinkLauncher = PlatformFactory.createLinkLauncher()

    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    private val browserRepository: BrowserRepository = BrowserRepositoryImpl(browserDiscovery)
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

    val discoverBrowsersUseCase: DiscoverBrowsersUseCase =
        DiscoverBrowsersUseCase(browserRepository, selfBundleId = ownBundleId)
    val isDefaultBrowserUseCase: IsDefaultBrowserUseCase =
        IsDefaultBrowserUseCase(defaultBrowserService)
    val openDefaultBrowserSettingsUseCase: OpenDefaultBrowserSettingsUseCase =
        OpenDefaultBrowserSettingsUseCase(defaultBrowserService)
    val getCanOpenSystemSettingsUseCase: GetCanOpenSystemSettingsUseCase =
        GetCanOpenSystemSettingsUseCase(defaultBrowserService)

    val pickerCoordinator: PickerCoordinator = PickerCoordinator(
        discoverBrowsers = discoverBrowsersUseCase,
        getSettings = getSettingsFlowUseCase,
        launcher = linkLauncher,
        scope = coroutineScope,
    )

    init {
        coroutineScope.launch {
            try {
                val browsers = discoverBrowsersUseCase()
                println("Discovered ${browsers.size} browser(s):")
                browsers.forEach { browser ->
                    val version = browser.version ?: "(no version)"
                    println(
                        "  - ${browser.displayName} $version " +
                            "(${browser.bundleId}) at ${browser.applicationPath}",
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                println("[ERROR] Browser discovery failed: ${t.message}")
            }
        }
    }

    fun newSettingsViewModel(): SettingsViewModel = SettingsViewModel(
        getSettings = getSettingsFlowUseCase,
        updateTheme = updateThemeUseCase,
        updateLanguage = updateLanguageUseCase,
        setAutoStart = setAutoStartUseCase,
        setBrowserExcluded = setBrowserExcludedUseCase,
        discoverBrowsers = discoverBrowsersUseCase,
        isDefaultBrowserUseCase = isDefaultBrowserUseCase,
        openDefaultBrowserSettings = openDefaultBrowserSettingsUseCase,
        getCanOpenSystemSettings = getCanOpenSystemSettingsUseCase,
        scope = coroutineScope,
    )
}
