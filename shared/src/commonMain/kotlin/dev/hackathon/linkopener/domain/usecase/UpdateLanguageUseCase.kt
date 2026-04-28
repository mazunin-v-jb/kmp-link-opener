package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class UpdateLanguageUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(language: AppLanguage) = repository.updateLanguage(language)
}
