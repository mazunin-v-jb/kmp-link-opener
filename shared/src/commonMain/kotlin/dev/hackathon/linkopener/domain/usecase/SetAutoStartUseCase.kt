package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.domain.repository.SettingsRepository

class SetAutoStartUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAutoStart(enabled)
}
