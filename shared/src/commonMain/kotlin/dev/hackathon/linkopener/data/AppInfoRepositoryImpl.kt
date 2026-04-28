package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.core.model.AppInfo
import dev.hackathon.linkopener.domain.repository.AppInfoRepository

class AppInfoRepositoryImpl : AppInfoRepository {
    override fun getAppInfo(): AppInfo = AppInfo(
        name = APP_NAME,
        version = APP_VERSION,
    )

    private companion object {
        const val APP_NAME = "Link Opener"
        const val APP_VERSION = "0.1.0"
    }
}
