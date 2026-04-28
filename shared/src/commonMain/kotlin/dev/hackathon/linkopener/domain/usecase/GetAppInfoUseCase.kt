package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.AppInfo
import dev.hackathon.linkopener.domain.repository.AppInfoRepository

class GetAppInfoUseCase(
    private val repository: AppInfoRepository,
) {
    operator fun invoke(): AppInfo = repository.getAppInfo()
}
