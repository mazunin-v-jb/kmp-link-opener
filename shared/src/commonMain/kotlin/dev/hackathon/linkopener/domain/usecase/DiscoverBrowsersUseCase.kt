package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.domain.repository.BrowserRepository

class DiscoverBrowsersUseCase(
    private val repository: BrowserRepository,
    private val selfBundleId: String? = null,
) {
    suspend operator fun invoke(): List<Browser> {
        val all = repository.getInstalledBrowsers()
        return if (selfBundleId == null) all
        else all.filterNot { it.bundleId == selfBundleId }
    }
}
