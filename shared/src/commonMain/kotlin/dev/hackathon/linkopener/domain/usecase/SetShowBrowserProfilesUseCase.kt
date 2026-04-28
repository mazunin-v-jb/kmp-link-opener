package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.domain.repository.SettingsRepository

class SetShowBrowserProfilesUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) =
        repository.setShowBrowserProfiles(enabled)
}
