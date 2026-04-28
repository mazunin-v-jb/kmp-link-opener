package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetBrowserExcludedUseCaseTest {

    @Test
    fun forwardsExcludeRequestToRepository() = runTest {
        val repo = RecordingRepository()
        val useCase = SetBrowserExcludedUseCase(repo)
        val id = BrowserId("com.apple.Safari")

        useCase(id, excluded = true)

        assertEquals(listOf(id to true), repo.calls)
    }

    @Test
    fun forwardsIncludeRequestToRepository() = runTest {
        val repo = RecordingRepository()
        val useCase = SetBrowserExcludedUseCase(repo)
        val id = BrowserId("com.google.Chrome")

        useCase(id, excluded = false)

        assertEquals(listOf(id to false), repo.calls)
    }

    @Test
    fun forwardsRepeatedTogglesInOrder() = runTest {
        val repo = RecordingRepository()
        val useCase = SetBrowserExcludedUseCase(repo)
        val a = BrowserId("a")
        val b = BrowserId("b")

        useCase(a, true)
        useCase(b, false)
        useCase(a, false)

        assertEquals(listOf(a to true, b to false, a to false), repo.calls)
    }

    private class RecordingRepository : SettingsRepository {
        val calls = mutableListOf<Pair<BrowserId, Boolean>>()
        override val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings.Default)

        override suspend fun updateTheme(theme: AppTheme) = error("not used")
        override suspend fun updateLanguage(language: AppLanguage) = error("not used")
        override suspend fun setAutoStart(enabled: Boolean) = error("not used")
        override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) {
            calls.add(id to excluded)
        }
    }
}
