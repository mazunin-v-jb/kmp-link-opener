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

    @Test
    fun excludesSelfBundleIdWhenProvided() = runTest {
        val all = listOf(
            Browser("dev.hackathon.linkopener", "Link Opener", "/Applications/Link Opener.app", "1.0.0"),
            Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"),
            Browser("com.google.Chrome", "Google Chrome", "/Applications/Google Chrome.app", "131"),
        )
        val useCase = DiscoverBrowsersUseCase(
            FakeRepository(all),
            selfBundleId = "dev.hackathon.linkopener",
        )

        val result = useCase()

        assertEquals(2, result.size)
        assertEquals(setOf("com.apple.Safari", "com.google.Chrome"), result.map { it.bundleId }.toSet())
    }

    @Test
    fun selfBundleIdFilterAppliesOnlyToExactMatch() = runTest {
        val all = listOf(
            Browser("dev.hackathon.linkopener", "Link Opener", "/Applications/Link Opener.app", "1.0.0"),
            Browser("dev.hackathon.linkopener.dev", "Link Opener Dev", "/Applications/Link Opener Dev.app", "1.0.0"),
        )
        val useCase = DiscoverBrowsersUseCase(
            FakeRepository(all),
            selfBundleId = "dev.hackathon.linkopener",
        )

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals("dev.hackathon.linkopener.dev", result[0].bundleId)
    }

    private class FakeRepository(private val value: List<Browser>) : BrowserRepository {
        override suspend fun getInstalledBrowsers(): List<Browser> = value
        override suspend fun refresh(): List<Browser> = value
    }
}
