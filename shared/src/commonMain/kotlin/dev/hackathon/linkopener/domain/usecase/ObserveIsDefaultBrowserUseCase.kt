package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.platform.DefaultBrowserService
import kotlinx.coroutines.flow.Flow

class ObserveIsDefaultBrowserUseCase(
    private val service: DefaultBrowserService,
) {
    operator fun invoke(): Flow<Boolean> = service.observeIsDefaultBrowser()
}
