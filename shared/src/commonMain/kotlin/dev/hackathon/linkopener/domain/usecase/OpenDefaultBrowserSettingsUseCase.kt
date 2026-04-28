package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.platform.DefaultBrowserService

class OpenDefaultBrowserSettingsUseCase(
    private val service: DefaultBrowserService,
) {
    suspend operator fun invoke(): Boolean = service.openSystemSettings()
}
