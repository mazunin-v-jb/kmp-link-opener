package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class GetSettingsFlowUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): StateFlow<AppSettings> = repository.settings
}
