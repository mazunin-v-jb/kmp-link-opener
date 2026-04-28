package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.repository.BrowserRepository
import dev.hackathon.linkopener.platform.BrowserDiscovery
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BrowserRepositoryImpl(
    private val discovery: BrowserDiscovery,
) : BrowserRepository {
    private val mutex = Mutex()
    private var cached: List<Browser>? = null

    override suspend fun getInstalledBrowsers(): List<Browser> = mutex.withLock {
        cached ?: discovery.discover().also { cached = it }
    }
}
