package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.platform.DefaultBrowserService

class GetCanOpenSystemSettingsUseCase(
    private val service: DefaultBrowserService,
) {
    operator fun invoke(): Boolean = service.canOpenSystemSettings
}
