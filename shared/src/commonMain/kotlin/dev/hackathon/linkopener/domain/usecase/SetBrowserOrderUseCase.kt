package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class SetBrowserOrderUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(order: List<BrowserId>) =
        repository.setBrowserOrder(order)
}
