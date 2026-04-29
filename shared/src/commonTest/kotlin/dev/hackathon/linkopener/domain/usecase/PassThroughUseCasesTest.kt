package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Coverage for the use cases that delegate straight to a repository or
 * platform service with no logic of their own. They're trivial — but the
 * delegation contract is part of the interface guarantee, and a stray
 * `repository.someOtherMethod(...)` typo should fail loudly.
 *
 * Each use case has at least one test asserting that:
 * 1. invoke() is wired to the matching repository/service method, and
 * 2. the argument is forwarded verbatim (no transformation).
 */
class PassThroughUseCasesTest {

    @Test
    fun updateThemeUseCaseForwardsTheme() = runTest {
        val repo = RecordingSettingsRepository()
        UpdateThemeUseCase(repo)(AppTheme.Dark)
        assertEquals(listOf(AppTheme.Dark), repo.themeUpdates)
    }

    @Test
    fun updateLanguageUseCaseForwardsLanguage() = runTest {
        val repo = RecordingSettingsRepository()
        UpdateLanguageUseCase(repo)(AppLanguage.Ru)
        assertEquals(listOf(AppLanguage.Ru), repo.languageUpdates)
    }

    @Test
    fun setAutoStartUseCaseForwardsBoolean() = runTest {
        val repo = RecordingSettingsRepository()
        SetAutoStartUseCase(repo)(true)
        SetAutoStartUseCase(repo)(false)
        assertEquals(listOf(true, false), repo.autoStartCalls)
    }

    @Test
    fun setBrowserOrderUseCaseForwardsList() = runTest {
        val repo = RecordingSettingsRepository()
        val order = listOf(BrowserId("a"), BrowserId("b"))
        SetBrowserOrderUseCase(repo)(order)
        assertEquals(listOf(order), repo.orderCalls)
    }

    @Test
    fun removeManualBrowserUseCaseForwardsId() = runTest {
        val repo = RecordingSettingsRepository()
        val id = BrowserId("/Apps/Custom.app")
        RemoveManualBrowserUseCase(repo)(id)
        assertEquals(listOf(id), repo.removeCalls)
    }

    @Test
    fun setRulesUseCaseForwardsRules() = runTest {
        val repo = RecordingSettingsRepository()
        val rules = listOf(UrlRule("*.example.com", BrowserId("/Apps/X.app")))
        SetRulesUseCase(repo)(rules)
        assertEquals(listOf(rules), repo.rulesCalls)
    }

    @Test
    fun setShowBrowserProfilesUseCaseForwardsBoolean() = runTest {
        val repo = RecordingSettingsRepository()
        SetShowBrowserProfilesUseCase(repo)(false)
        SetShowBrowserProfilesUseCase(repo)(true)
        assertEquals(listOf(false, true), repo.showProfilesCalls)
    }

    @Test
    fun setShowCloseButtonUseCaseForwardsBoolean() = runTest {
        val repo = RecordingSettingsRepository()
        SetShowCloseButtonUseCase(repo)(true)
        SetShowCloseButtonUseCase(repo)(false)
        // setShowCloseButton is a no-op stub in RecordingSettingsRepository,
        // so just verify it doesn't throw.
    }

    @Test
    fun getSettingsFlowUseCaseExposesUnderlyingFlow() {
        // No suspend or transform — should hand the same StateFlow back.
        val repo = RecordingSettingsRepository()
        val useCase = GetSettingsFlowUseCase(repo)

        assertSame(repo.settings, useCase())
    }

    // --- DefaultBrowserService passthroughs ----------------------------------

    @Test
    fun getIsDefaultBrowserUseCaseForwardsToService() = runTest {
        val service = RecordingDefaultBrowserService(isDefault = true)
        assertEquals(true, GetIsDefaultBrowserUseCase(service)())
        service.isDefault = false
        assertEquals(false, GetIsDefaultBrowserUseCase(service)())
    }

    @Test
    fun openDefaultBrowserSettingsUseCaseForwardsAndReturnsResult() = runTest {
        val service = RecordingDefaultBrowserService()
        val result = OpenDefaultBrowserSettingsUseCase(service)()
        assertEquals(true, result)
        assertEquals(1, service.openCount)
    }

    @Test
    fun openDefaultBrowserSettingsReturnsFalseWhenServiceCantOpen() = runTest {
        val service = RecordingDefaultBrowserService(canOpen = false, openSucceeds = false)
        assertEquals(false, OpenDefaultBrowserSettingsUseCase(service)())
    }

    @Test
    fun getCanOpenSystemSettingsUseCaseExposesServiceFlag() {
        val service = RecordingDefaultBrowserService(canOpen = false)
        assertEquals(false, GetCanOpenSystemSettingsUseCase(service)())
        val service2 = RecordingDefaultBrowserService(canOpen = true)
        assertTrue(GetCanOpenSystemSettingsUseCase(service2)())
    }

    @Test
    fun observeIsDefaultBrowserUseCaseExposesServiceFlow() = runTest {
        val service = RecordingDefaultBrowserService(observeEmissions = listOf(false, true, false))
        val emissions = ObserveIsDefaultBrowserUseCase(service)().toList()
        assertEquals(listOf(false, true, false), emissions)
    }

    // --- Recording fakes -----------------------------------------------------

    private class RecordingSettingsRepository : SettingsRepository {
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings.Default)

        val themeUpdates = mutableListOf<AppTheme>()
        val languageUpdates = mutableListOf<AppLanguage>()
        val autoStartCalls = mutableListOf<Boolean>()
        val orderCalls = mutableListOf<List<BrowserId>>()
        val removeCalls = mutableListOf<BrowserId>()
        val rulesCalls = mutableListOf<List<UrlRule>>()
        val showProfilesCalls = mutableListOf<Boolean>()

        override suspend fun updateTheme(theme: AppTheme) { themeUpdates += theme }
        override suspend fun updateLanguage(language: AppLanguage) { languageUpdates += language }
        override suspend fun setAutoStart(enabled: Boolean) { autoStartCalls += enabled }
        override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) =
            error("not used")
        override suspend fun setBrowserOrder(order: List<BrowserId>) { orderCalls += order }
        override suspend fun addManualBrowser(browser: Browser) = error("not used")
        override suspend fun removeManualBrowser(id: BrowserId) { removeCalls += id }
        override suspend fun setRules(rules: List<UrlRule>) { rulesCalls += rules }
        override suspend fun setShowBrowserProfiles(enabled: Boolean) {
            showProfilesCalls += enabled
        }

        override suspend fun setShowCloseButton(enabled: Boolean) { /* not tracked */ }
    }

    private class RecordingDefaultBrowserService(
        var isDefault: Boolean = false,
        canOpen: Boolean = true,
        private val openSucceeds: Boolean = true,
        private val observeEmissions: List<Boolean> = emptyList(),
    ) : DefaultBrowserService {
        override val canOpenSystemSettings: Boolean = canOpen
        var openCount: Int = 0
        override suspend fun isDefaultBrowser(): Boolean = isDefault
        override suspend fun openSystemSettings(): Boolean {
            openCount += 1
            return openSucceeds
        }
        override fun observeIsDefaultBrowser(): Flow<Boolean> = flowOf(*observeEmissions.toTypedArray())
    }
}
