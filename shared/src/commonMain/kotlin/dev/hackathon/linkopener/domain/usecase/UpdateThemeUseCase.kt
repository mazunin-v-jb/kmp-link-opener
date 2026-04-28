package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class UpdateThemeUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(theme: AppTheme) = repository.updateTheme(theme)
}
