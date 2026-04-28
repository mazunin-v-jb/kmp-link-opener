package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AddManualBrowserUseCaseTest {

    private val ownBundleId = "dev.hackathon.linkopener"

    @Test
    fun happyPathPersistsAndReportsAdded() = runTest {
        val browser = browser("com.example.fake", "/Apps/Fake.app")
        val repo = InMemorySettingsRepository()
        val useCase = AddManualBrowserUseCase(
            extractor = StaticExtractor(ExtractResult.Success(browser)),
            settings = repo,
            browsers = StaticBrowserRepository(emptyList()),
            ownBundleId = ownBundleId,
        )

        val result = useCase("/Apps/Fake.app")

        assertIs<AddManualBrowserUseCase.AddResult.Added>(result)
        assertEquals(browser, result.browser)
        assertEquals(listOf(browser), repo.settings.value.manualBrowsers)
    }

    @Test
    fun duplicateAgainstAlreadyManualReturnsDuplicate() = runTest {
        val existing = browser("com.example.fake", "/Apps/Fake.app")
        val repo = InMemorySettingsRepository(initial = AppSettings(manualBrowsers = listOf(existing)))
        // Extractor reports a slightly different display name to make sure path
        // is what the dedupe logic keys off — not the entire Browser object.
        val useCase = AddManualBrowserUseCase(
            extractor = StaticExtractor(ExtractResult.Success(existing.copy(displayName = "Renamed"))),
            settings = repo,
            browsers = StaticBrowserRepository(listOf(existing)),
            ownBundleId = ownBundleId,
        )

        val result = useCase("/Apps/Fake.app")

        assertIs<AddManualBrowserUseCase.AddResult.Duplicate>(result)
        assertEquals(listOf(existing), repo.settings.value.manualBrowsers)
    }

    @Test
    fun duplicateAgainstAutoDiscoveredReturnsDuplicate() = runTest {
        val safari = browser("com.apple.Safari", "/Applications/Safari.app")
        val repo = InMemorySettingsRepository()
        val useCase = AddManualBrowserUseCase(
            extractor = StaticExtractor(ExtractResult.Success(safari)),
            settings = repo,
            // Repository already has Safari as discovered — manual addition must be rejected.
            browsers = StaticBrowserRepository(listOf(safari)),
            ownBundleId = ownBundleId,
        )

        val result = useCase("/Applications/Safari.app")

        assertIs<AddManualBrowserUseCase.AddResult.Duplicate>(result)
        assertEquals(emptyList(), repo.settings.value.manualBrowsers)
    }

    @Test
    fun selfBundleIdReturnsIsSelf() = runTest {
        val self = browser(ownBundleId, "/Applications/Link Opener.app")
        val repo = InMemorySettingsRepository()
        val useCase = AddManualBrowserUseCase(
            extractor = StaticExtractor(ExtractResult.Success(self)),
            settings = repo,
            browsers = StaticBrowserRepository(emptyList()),
            ownBundleId = ownBundleId,
        )

        val result = useCase("/Applications/Link Opener.app")

        assertIs<AddManualBrowserUseCase.AddResult.IsSelf>(result)
        assertEquals(emptyList(), repo.settings.value.manualBrowsers)
    }

    @Test
    fun extractorFailureSurfacesAsInvalidApp() = runTest {
        val repo = InMemorySettingsRepository()
        val useCase = AddManualBrowserUseCase(
            extractor = StaticExtractor(ExtractResult.Failure("Missing Info.plist")),
            settings = repo,
            browsers = StaticBrowserRepository(emptyList()),
            ownBundleId = ownBundleId,
        )

        val result = useCase("/Apps/Bogus")

        assertIs<AddManualBrowserUseCase.AddResult.InvalidApp>(result)
        assertEquals("Missing Info.plist", result.reason)
        assertEquals(emptyList(), repo.settings.value.manualBrowsers)
    }

    private fun browser(bundleId: String, path: String): Browser =
        Browser(bundleId = bundleId, displayName = "Display", applicationPath = path, version = "1.0")

    private class StaticExtractor(private val result: ExtractResult) : BrowserMetadataExtractor {
        override suspend fun extract(path: String): ExtractResult = result
    }

    private class StaticBrowserRepository(private val browsers: List<Browser>) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = browsers
        override suspend fun refresh(): List<Browser> = browsers
    }

    private class InMemorySettingsRepository(initial: AppSettings = AppSettings.Default) : SettingsRepository {
        private val _settings = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = _settings
        override suspend fun updateTheme(theme: AppTheme) = error("not used")
        override suspend fun updateLanguage(language: AppLanguage) = error("not used")
        override suspend fun setAutoStart(enabled: Boolean) = error("not used")
        override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) = error("not used")
        override suspend fun setBrowserOrder(order: List<BrowserId>) = error("not used")
        override suspend fun addManualBrowser(browser: Browser) {
            _settings.update {
                if (it.manualBrowsers.any { b -> b.applicationPath == browser.applicationPath }) it
                else it.copy(manualBrowsers = it.manualBrowsers + browser)
            }
        }
        override suspend fun removeManualBrowser(id: BrowserId) {
            _settings.update {
                it.copy(
                    manualBrowsers = it.manualBrowsers.filterNot { b ->
                        BrowserId(b.applicationPath) == id
                    },
                )
            }
        }
    }
}
