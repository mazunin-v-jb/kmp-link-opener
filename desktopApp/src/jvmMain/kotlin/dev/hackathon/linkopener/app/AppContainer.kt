package dev.hackathon.linkopener.app

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
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
    private val browserMetadataExtractor: BrowserMetadataExtractor =
        PlatformFactory.createBrowserMetadataExtractor()

    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        store = settingsStore,
        json = json,
        autoStartManager = autoStartManager,
    )
    private val browserRepository: BrowserRepository =
        BrowserRepositoryImpl(browserDiscovery, settingsRepository)

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

    private val ruleEngine: RuleEngine = RuleEngine(
        debug = DEBUG_LOGGING,
        log = ::println,
    )

    val discoverBrowsersUseCase: DiscoverBrowsersUseCase =
        DiscoverBrowsersUseCase(browserRepository, selfBundleId = ownBundleId)
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
            try {
                val browsers = discoverBrowsersUseCase()
                if (DEBUG_LOGGING) {
                    println("Discovered ${browsers.size} browser(s):")
                    browsers.forEach { browser ->
                        val version = browser.version ?: "(no version)"
                        println(
                            "  - ${browser.displayName} $version " +
                                "(${browser.bundleId}) at ${browser.applicationPath}",
                        )
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // Errors always surface — they indicate a real problem we want
                // to see in Console.app even outside debug mode.
                System.err.println("[ERROR] Browser discovery failed: ${t.message}")
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
        discoverBrowsers = discoverBrowsersUseCase,
        observeIsDefaultBrowser = observeIsDefaultBrowserUseCase,
        getIsDefaultBrowser = getIsDefaultBrowserUseCase,
        openDefaultBrowserSettings = openDefaultBrowserSettingsUseCase,
        getCanOpenSystemSettings = getCanOpenSystemSettingsUseCase,
        scope = coroutineScope,
        applyLocale = ::applyJvmLocale,
    )

    // Translates user-selected AppLanguage into the JVM Locale.getDefault()
    // override that Compose Resources reads through. Falls back to "en" for
    // the System mode when the host locale isn't one of our supported
    // languages.
    private fun applyJvmLocale(language: dev.hackathon.linkopener.core.model.AppLanguage) {
        val tag = when (language) {
            dev.hackathon.linkopener.core.model.AppLanguage.En -> "en"
            dev.hackathon.linkopener.core.model.AppLanguage.Ru -> "ru"
            dev.hackathon.linkopener.core.model.AppLanguage.System -> {
                val systemTag = java.util.Locale.getDefault().language
                if (systemTag == "ru") "ru" else "en"
            }
        }
        val target = java.util.Locale.forLanguageTag(tag)
        if (java.util.Locale.getDefault() != target) {
            java.util.Locale.setDefault(target)
        }
    }

    private companion object {
        // Set `-Dlinkopener.debug=true` (e.g. via JVM args in IDE run config or
        // VM options when launching the .app from Terminal) to get the discovery
        // dump and any other diagnostic prints in stdout.
        val DEBUG_LOGGING: Boolean = System.getProperty("linkopener.debug") == "true"
    }
}
