package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserRepositoryImplTest {

    @Test
    fun cachesDiscoveryResultBetweenCalls() = runTest {
        val discovery = CountingDiscovery(
            listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0")),
        )
        val repository = BrowserRepositoryImpl(discovery)

        repeat(3) { repository.getInstalledBrowsers() }

        assertEquals(1, discovery.callCount)
    }

    @Test
    fun returnsSameListEachCall() = runTest {
        val list = listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"))
        val repository = BrowserRepositoryImpl(CountingDiscovery(list))

        val first = repository.getInstalledBrowsers()
        val second = repository.getInstalledBrowsers()

        assertEquals(list, first)
        assertEquals(list, second)
    }

    @Test
    fun refreshBypassesCacheAndUpdatesIt() = runTest {
        val initial = listOf(Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.0"))
        val updated = initial + Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", "126")
        val discovery = SwitchingDiscovery(listOf(initial, updated))
        val repository = BrowserRepositoryImpl(discovery)

        // Populate the cache.
        assertEquals(initial, repository.getInstalledBrowsers())
        // Force a fresh scan — must run discovery again and return the new list.
        assertEquals(updated, repository.refresh())
        // Cache now reflects the refreshed value, no further discovery calls.
        assertEquals(updated, repository.getInstalledBrowsers())
        assertEquals(2, discovery.callCount)
    }

    private class CountingDiscovery(private val value: List<Browser>) : BrowserDiscovery {
        var callCount = 0
            private set

        override suspend fun discover(): List<Browser> {
            callCount += 1
            return value
        }
    }

    private class SwitchingDiscovery(private val sequence: List<List<Browser>>) : BrowserDiscovery {
        var callCount = 0
            private set

        override suspend fun discover(): List<Browser> {
            val index = callCount.coerceAtMost(sequence.lastIndex)
            callCount += 1
            return sequence[index]
        }
    }
}
