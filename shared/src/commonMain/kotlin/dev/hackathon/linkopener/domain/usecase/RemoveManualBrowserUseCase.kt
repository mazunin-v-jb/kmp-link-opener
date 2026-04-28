package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class RemoveManualBrowserUseCase(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(id: BrowserId) = settings.removeManualBrowser(id)
}
