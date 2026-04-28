package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.APP_VERSION
import dev.hackathon.linkopener.core.model.AppInfo
import dev.hackathon.linkopener.domain.repository.AppInfoRepository

class AppInfoRepositoryImpl : AppInfoRepository {
    override fun getAppInfo(): AppInfo = AppInfo(
        name = APP_NAME,
        // APP_VERSION lives in build/generated/version/.../BuildVersion.kt,
        // produced by :shared's generateBuildVersion task from `linkopener.version`
        // in root gradle.properties. Single source of truth for the displayed
        // app version — same string the DMG packaging uses.
        version = APP_VERSION,
    )

    private companion object {
        const val APP_NAME = "Link Opener"
    }
}
