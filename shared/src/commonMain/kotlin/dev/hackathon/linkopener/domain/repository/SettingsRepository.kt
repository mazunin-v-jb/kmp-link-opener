package dev.hackathon.linkopener.domain.repository

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun setAutoStart(enabled: Boolean)
    suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean)
    suspend fun setBrowserOrder(order: List<BrowserId>)
}
