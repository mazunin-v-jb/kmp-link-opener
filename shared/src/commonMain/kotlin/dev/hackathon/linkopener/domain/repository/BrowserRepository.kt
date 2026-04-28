package dev.hackathon.linkopener.domain.repository

import dev.hackathon.linkopener.core.model.Browser

interface BrowserRepository {
    suspend fun getInstalledBrowsers(): List<Browser>
}
