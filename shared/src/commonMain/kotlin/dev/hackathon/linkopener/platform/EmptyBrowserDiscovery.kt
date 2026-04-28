package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

class EmptyBrowserDiscovery(private val osName: String) : BrowserDiscovery {
    override suspend fun discover(): List<Browser> {
        println("[INFO] Browser discovery is not implemented for OS '$osName'; returning empty list.")
        return emptyList()
    }
}
