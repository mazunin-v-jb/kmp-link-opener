package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverBrowsersUseCaseTest {

    @Test
    fun returnsBrowsersFromRepository() = runTest {
        val expected = listOf(
            Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"),
            Browser("com.google.Chrome", "Google Chrome", "/Applications/Google Chrome.app", "131.0.6778.86"),
        )
        val useCase = DiscoverBrowsersUseCase(FakeRepository(expected))

        assertEquals(expected, useCase())
    }

    @Test
    fun returnsEmptyListWhenRepositoryHasNothing() = runTest {
        val useCase = DiscoverBrowsersUseCase(FakeRepository(emptyList()))

        assertEquals(emptyList(), useCase())
    }

    private class FakeRepository(private val value: List<Browser>) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = value
    }
}
