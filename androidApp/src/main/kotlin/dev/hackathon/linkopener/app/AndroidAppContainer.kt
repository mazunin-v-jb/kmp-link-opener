package dev.hackathon.linkopener.app

import android.content.Context
import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation
import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
import dev.hackathon.linkopener.data.BrowserIconRepository
import dev.hackathon.linkopener.data.BrowserRepositoryImpl
import dev.hackathon.linkopener.data.SettingsRepositoryImpl
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
import dev.hackathon.linkopener.platform.NoOpAutoStartManager
import dev.hackathon.linkopener.platform.UnsupportedManualBrowserExtractor
import dev.hackathon.linkopener.platform.android.AndroidBrowserDiscovery
import dev.hackathon.linkopener.platform.android.AndroidBrowserIconLoader
import dev.hackathon.linkopener.platform.android.AndroidDefaultBrowserService
import dev.hackathon.linkopener.platform.android.AndroidLinkLauncher
import dev.hackathon.linkopener.platform.android.AndroidUrlReceiver
import dev.hackathon.linkopener.ui.picker.PickerCoordinator
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Android-side mirror of `:desktopApp`'s `AppContainer`. Same domain
 * graph (use cases, repositories, picker coordinator), but wired to
 * Android platform implementations and missing the desktop-only bits
 * (tray, single-instance guard, JVM locale juggling).
 *
 * Owned by `LinkOpenerApplication` so both `MainActivity` and
 * `PickerActivity` share a single instance — discovery prefetch runs
 * once at process startup and the cache is reused across activities.
 */
class AndroidAppContainer(context: Context) {

    val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val appContext: Context = context.applicationContext

    private val ownPackageName: String = appContext.packageName

    private val json: Json = Json { ignoreUnknownKeys = true }

    private val settingsStore: Settings = Settings()

    private val autoStartManager = NoOpAutoStartManager()
    private val browserDiscovery = AndroidBrowserDiscovery(appContext, ownPackageName)
    val urlReceiver: AndroidUrlReceiver = AndroidUrlReceiver()
    // Concrete reference held so we can call forceRecheck() — the
    // base interface (DefaultBrowserService) doesn't expose it.
    private val androidDefaultBrowserService =
        AndroidDefaultBrowserService(appContext, ownPackageName)
    private val defaultBrowserService = androidDefaultBrowserService
    private val linkLauncher = AndroidLinkLauncher(appContext)

    /**
     * Force-emits a fresh `isDefaultBrowser()` reading on the
     * `observeIsDefaultBrowser()` flow that `SettingsViewModel` is
     * subscribed to. `MainActivity.onResume` calls this so the
     * "Set as default" banner updates immediately after the user
     * returns from the system dialog / Default Apps page.
     */
    fun recheckDefaultBrowser() {
        androidDefaultBrowserService.forceRecheck()
    }
    private val browserMetadataExtractor = UnsupportedManualBrowserExtractor("Android")
    private val browserIconLoader = AndroidBrowserIconLoader(appContext)

    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(
        store = settingsStore,
        json = json,
        autoStartManager = autoStartManager,
    )
    private val browserRepository: BrowserRepository =
        BrowserRepositoryImpl(browserDiscovery, settingsRepository)
    val browserIconRepository: BrowserIconRepository = BrowserIconRepository(browserIconLoader)

    val getAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)
    val getSettingsFlowUseCase = GetSettingsFlowUseCase(settingsRepository)
    val updateThemeUseCase = UpdateThemeUseCase(settingsRepository)
    val updateLanguageUseCase = UpdateLanguageUseCase(settingsRepository)
    val setAutoStartUseCase = SetAutoStartUseCase(settingsRepository)
    val setBrowserExcludedUseCase = SetBrowserExcludedUseCase(settingsRepository)
    val setBrowserOrderUseCase = SetBrowserOrderUseCase(settingsRepository)
    val addManualBrowserUseCase = AddManualBrowserUseCase(
        extractor = browserMetadataExtractor,
        settings = settingsRepository,
        browsers = browserRepository,
        ownBundleId = ownPackageName,
    )
    val removeManualBrowserUseCase = RemoveManualBrowserUseCase(settingsRepository)
    val setRulesUseCase = SetRulesUseCase(settingsRepository)
    val setShowBrowserProfilesUseCase = SetShowBrowserProfilesUseCase(settingsRepository)

    private val ruleEngine = RuleEngine(debug = false, log = ::println)

    val discoverBrowsersUseCase = DiscoverBrowsersUseCase(browserRepository, selfBundleId = ownPackageName)
    val observeIsDefaultBrowserUseCase = ObserveIsDefaultBrowserUseCase(defaultBrowserService)
    val getIsDefaultBrowserUseCase = GetIsDefaultBrowserUseCase(defaultBrowserService)
    val openDefaultBrowserSettingsUseCase = OpenDefaultBrowserSettingsUseCase(defaultBrowserService)
    val getCanOpenSystemSettingsUseCase = GetCanOpenSystemSettingsUseCase(defaultBrowserService)

    val pickerCoordinator = PickerCoordinator(
        discoverBrowsers = discoverBrowsersUseCase,
        getSettings = getSettingsFlowUseCase,
        launcher = linkLauncher,
        ruleEngine = ruleEngine,
        iconRepository = browserIconRepository,
        scope = coroutineScope,
    )

    init {
        // Warm-up discovery + icon prefetch so the first picker open finds
        // browsers + icons cached. Same shape as desktop's AppContainer.init.
        coroutineScope.launch {
            runCatchingNonCancellation { discoverBrowsersUseCase() }
                .onSuccess { browsers ->
                    browserIconRepository.prefetch(
                        scope = coroutineScope,
                        applicationPaths = browsers.map { it.applicationPath },
                    )
                }
                .onFailure {
                    System.err.println("[ERROR] Browser discovery failed: ${it.message}")
                }
        }

        // Wire URL receiver into picker. On Android each PickerActivity intent
        // calls AndroidUrlReceiver.submit(url); the receiver re-dispatches into
        // the picker coordinator. Same indirection layer as desktop so the
        // PickerCoordinator stays platform-agnostic.
        urlReceiver.start { url -> pickerCoordinator.handleIncomingUrl(url) }
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
        // Locale switching is desktop-specific (we manipulate JVM Locale
        // because Compose Resources reads it). On Android the system handles
        // locale via Configuration; for v1 we accept the system locale.
        applyLocale = { /* no-op on Android v1 */ },
    )
}
