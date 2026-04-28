package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class SetBrowserExcludedUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(id: BrowserId, excluded: Boolean) =
        repository.setBrowserExcluded(id, excluded)
}
