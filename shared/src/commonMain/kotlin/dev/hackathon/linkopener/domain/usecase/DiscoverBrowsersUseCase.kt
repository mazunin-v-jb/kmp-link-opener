package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.repository.BrowserRepository

class DiscoverBrowsersUseCase(private val repository: BrowserRepository) {
    suspend operator fun invoke(): List<Browser> = repository.getInstalledBrowsers()
}
