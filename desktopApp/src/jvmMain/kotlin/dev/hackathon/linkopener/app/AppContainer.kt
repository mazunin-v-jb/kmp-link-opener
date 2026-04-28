package dev.hackathon.linkopener.app

import dev.hackathon.linkopener.data.AppInfoRepositoryImpl
import dev.hackathon.linkopener.domain.repository.AppInfoRepository
import dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCase

class AppContainer {
    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()

    val getAppInfoUseCase: GetAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)
}
