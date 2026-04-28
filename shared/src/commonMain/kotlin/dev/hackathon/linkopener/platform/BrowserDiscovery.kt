package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

interface BrowserDiscovery {
    suspend fun discover(): List<Browser>
}
