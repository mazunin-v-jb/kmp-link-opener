package dev.hackathon.linkopener.app

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation
import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
import dev.hackathon.linkopener.data.BrowserIconRepository
import dev.hackathon.linkopener.data.BrowserRepositoryImpl
import dev.hackathon.linkopener.data.SettingsRepositoryImpl
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.RuleEngine
import dev.hackathon.linkopener.domain.repository.AppInfoRepository
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.domain.usecase.AddManualBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCase
import dev.hackathon.linkopener.domain.usecase.GetCanOpenSystemSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.GetIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.domain.usecase.ObserveIsDefaultBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.OpenDefaultBrowserSettingsUseCase
import dev.hackathon.linkopener.domain.usecase.RemoveManualBrowserUseCase
import dev.hackathon.linkopener.domain.usecase.SetAutoStartUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserExcludedUseCase
import dev.hackathon.linkopener.domain.usecase.SetBrowserOrderUseCase
import dev.hackathon.linkopener.domain.usecase.SetRulesUseCase
import dev.hackathon.linkopener.domain.usecase.SetShowBrowserProfilesUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateLanguageUseCase
import dev.hackathon.linkopener.domain.usecase.UpdateThemeUseCase
import dev.hackathon.linkopener.platform.AutoStartManager
import dev.hackathon.linkopener.platform.BrowserDiscovery
import dev.hackathon.linkopener.platform.BrowserIconLoader
import dev.hackathon.linkopener.platform.DefaultBrowserService
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.platform.LinkLauncher
import dev.hackathon.linkopener.platform.PlatformFactory
import dev.hackathon.linkopener.platform.UrlReceiver
import dev.hackathon.linkopener.platform.windows.WindowsBrowserDiscovery
import dev.hackathon.linkopener.ui.picker.PickerCoordinator
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppContainer {

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val currentOs: HostOs = PlatformFactory.currentOs

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val settingsStore: Settings = Settings()

    // Must match nativeDistributions.macOS.bundleID in desktopApp/build.gradle.kts.
    // Used to recognise ourselves when reading the system's default-browser binding
    // and to block manually adding ourselves in AddManualBrowserUseCase.
    private val ownBundleId = "dev.hackathon.linkopener"

    // The bundleId value our app gets when Windows discovery scans
    // StartMenuInternet — that registry sub-key is what WindowsBrowserDiscovery
    // sets as bundleId, so the macOS reverse-DNS id never matches there.
    private val selfBundleIdForDiscovery: String = when (currentOs) {
        HostOs.Windows -> WindowsBrowserDiscovery.OWN_START_MENU_KEY
        else -> ownBundleId
    }

    private val autoStartManager: AutoStartManager = PlatformFactory.createAutoStartManager()
    private val browserDiscovery: BrowserDiscovery = PlatformFactory.createBrowserDiscovery()
    val urlReceiver: UrlReceiver = PlatformFactory.createUrlReceiver()
    private val defaultBrowserService: DefaultBrowserService =
        PlatformFactory.createDefaultBrowserService(ownBundleId = ownBundleId)
    private val linkLauncher: LinkLauncher = PlatformFactory.createLinkLauncher()
    private val browserMetadataExtractor: BrowserMetadataExtractor =
        PlatformFactory.createBrowserMetadataExtractor()
    private val browserIconLoader: BrowserIconLoader = PlatformFactory.createBrowserIconLoader()

    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        store = settingsStore,
        json = json,
        autoStartManager = autoStartManager,
    )
    private val browserRepository: BrowserRepository =
        BrowserRepositoryImpl(browserDiscovery, settingsRepository)
    val browserIconRepository: BrowserIconRepository = BrowserIconRepository(browserIconLoader)

    val getAppInfoUseCase: GetAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)
    val getSettingsFlowUseCase: GetSettingsFlowUseCase = GetSettingsFlowUseCase(settingsRepository)
    val updateThemeUseCase: UpdateThemeUseCase = UpdateThemeUseCase(settingsRepository)
    val updateLanguageUseCase: UpdateLanguageUseCase = UpdateLanguageUseCase(settingsRepository)
    val setAutoStartUseCase: SetAutoStartUseCase = SetAutoStartUseCase(settingsRepository)
    val setBrowserExcludedUseCase: SetBrowserExcludedUseCase =
        SetBrowserExcludedUseCase(settingsRepository)
    val setBrowserOrderUseCase: SetBrowserOrderUseCase =
        SetBrowserOrderUseCase(settingsRepository)
    val addManualBrowserUseCase: AddManualBrowserUseCase = AddManualBrowserUseCase(
        extractor = browserMetadataExtractor,
        settings = settingsRepository,
        browsers = browserRepository,
        ownBundleId = ownBundleId,
    )
    val removeManualBrowserUseCase: RemoveManualBrowserUseCase =
        RemoveManualBrowserUseCase(settingsRepository)
    val setRulesUseCase: SetRulesUseCase = SetRulesUseCase(settingsRepository)
    val setShowBrowserProfilesUseCase: SetShowBrowserProfilesUseCase =
        SetShowBrowserProfilesUseCase(settingsRepository)

    private val ruleEngine: RuleEngine = RuleEngine(
        debug = DebugFlags.enabled,
        log = ::println,
    )

    // Captured BEFORE any Locale.setDefault call so AppLanguage.System always
    // resolves to the OS-level locale, not whatever the user most recently
    // picked. Without this, switching En → System (or Ru → System) would
    // sticky-stay on the previous override because applyJvmLocale would read
    // its own override back from Locale.getDefault().
    private val systemLanguageTag: String = java.util.Locale.getDefault().language

    val discoverBrowsersUseCase: DiscoverBrowsersUseCase =
        DiscoverBrowsersUseCase(browserRepository, selfBundleId = selfBundleIdForDiscovery)
    val observeIsDefaultBrowserUseCase: ObserveIsDefaultBrowserUseCase =
        ObserveIsDefaultBrowserUseCase(defaultBrowserService)
    val getIsDefaultBrowserUseCase: GetIsDefaultBrowserUseCase =
        GetIsDefaultBrowserUseCase(defaultBrowserService)
    val openDefaultBrowserSettingsUseCase: OpenDefaultBrowserSettingsUseCase =
        OpenDefaultBrowserSettingsUseCase(defaultBrowserService)
    val getCanOpenSystemSettingsUseCase: GetCanOpenSystemSettingsUseCase =
        GetCanOpenSystemSettingsUseCase(defaultBrowserService)

    val pickerCoordinator: PickerCoordinator = PickerCoordinator(
        discoverBrowsers = discoverBrowsersUseCase,
        getSettings = getSettingsFlowUseCase,
        launcher = linkLauncher,
        ruleEngine = ruleEngine,
        iconRepository = browserIconRepository,
        scope = coroutineScope,
    )

    // Activation requests come from SingleInstanceGuard's listener thread when
    // a second copy of the app is launched. TrayHost subscribes and decides
    // whether to open Settings or just nudge the already-open window.
    private val _activationRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val activationRequests: SharedFlow<Unit> = _activationRequests.asSharedFlow()

    fun requestActivation() {
        _activationRequests.tryEmit(Unit)
    }

    init {
        // Apply the loaded language's locale to the JVM immediately, before
        // Compose ever composes anything. Otherwise the first composition
        // would use the system locale even if the user previously saved a
        // different choice.
        applyJvmLocale(settingsRepository.settings.value.language)

        // Warm-up discovery on startup so the picker shows instantly when the
        // first URL arrives instead of paying the 1-3s plutil latency. The
        // result is cached inside BrowserRepositoryImpl, so subsequent
        // discoverBrowsersUseCase() calls hit memory.
        coroutineScope.launch {
            runCatchingNonCancellation { discoverBrowsersUseCase() }
                .onSuccess { browsers ->
                    // Kick off icon extraction now so settings + picker render
                    // them on first paint. Each loader call is cheap (one
                    // FileSystemView.getSystemIcon hop on macOS/Windows or
                    // one .desktop walk on Linux), but we still want them off
                    // the main thread.
                    browserIconRepository.prefetch(
                        scope = coroutineScope,
                        applicationPaths = browsers.map { it.applicationPath },
                    )
                    if (DebugFlags.enabled) {
                        println("Discovered ${browsers.size} browser(s):")
                        browsers.forEach { browser ->
                            val version = browser.version ?: "(no version)"
                            println(
                                "  - ${browser.displayName} $version " +
                                    "(${browser.bundleId}) at ${browser.applicationPath}",
                            )
                        }
                    }
                }
                .onFailure {
                    // Errors always surface — they indicate a real problem we
                    // want to see in Console.app even outside debug mode.
                    System.err.println("[ERROR] Browser discovery failed: ${it.message}")
                }
        }
    }

    fun newSettingsViewModel(): SettingsViewModel = SettingsViewModel(
        getSettings = getSettingsFlowUseCase,
        updateTheme = updateThemeUseCase,
        updateLanguage = updateLanguageUseCase,
        setAutoStart = setAutoStartUseCase,
        setBrowserExcluded = setBrowserExcludedUseCase,
        setBrowserOrder = setBrowserOrderUseCase,
        addManualBrowser = addManualBrowserUseCase,
        removeManualBrowser = removeManualBrowserUseCase,
        setRules = setRulesUseCase,
        setShowBrowserProfiles = setShowBrowserProfilesUseCase,
        discoverBrowsers = discoverBrowsersUseCase,
        observeIsDefaultBrowser = observeIsDefaultBrowserUseCase,
        getIsDefaultBrowser = getIsDefaultBrowserUseCase,
        openDefaultBrowserSettings = openDefaultBrowserSettingsUseCase,
        getCanOpenSystemSettings = getCanOpenSystemSettingsUseCase,
        iconRepository = browserIconRepository,
        scope = coroutineScope,
        applyLocale = ::applyJvmLocale,
    )

    // Translates user-selected AppLanguage into the JVM Locale.getDefault()
    // override that Compose Resources reads through. Resolution itself is
    // delegated to the pure [resolveLocaleTag] helper so it can be unit-tested
    // without touching the global JVM Locale.
    private fun applyJvmLocale(language: AppLanguage) {
        val target = java.util.Locale.forLanguageTag(resolveLocaleTag(language, systemLanguageTag))
        if (java.util.Locale.getDefault() != target) {
            java.util.Locale.setDefault(target)
        }
    }

}

/**
 * Resolves an [AppLanguage] selection to the BCP-47 language tag that should
 * become `Locale.getDefault()`. The OS locale is passed in explicitly
 * ([systemLanguageTag]) so this stays pure — callers must capture it once,
 * before any `Locale.setDefault` happens, otherwise switching back to
 * `System` after the user picked En/Ru would re-resolve to the prior
 * override instead of the actual OS locale.
 *
 * Supported tags are `en` and `ru`; any other system tag falls back to `en`.
 */
internal fun resolveLocaleTag(
    language: AppLanguage,
    systemLanguageTag: String,
): String = when (language) {
    AppLanguage.En -> "en"
    AppLanguage.Ru -> "ru"
    AppLanguage.System ->
        if (systemLanguageTag == "ru") "ru" else "en"
}
