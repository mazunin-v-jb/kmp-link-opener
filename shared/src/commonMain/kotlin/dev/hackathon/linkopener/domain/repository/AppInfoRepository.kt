package dev.hackathon.linkopener.domain.repository

import dev.hackathon.linkopener.core.model.AppInfo

interface AppInfoRepository {
    fun getAppInfo(): AppInfo
}
