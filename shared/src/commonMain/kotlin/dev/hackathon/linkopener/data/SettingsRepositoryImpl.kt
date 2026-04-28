package dev.hackathon.linkopener.data

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.platform.AutoStartManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class SettingsRepositoryImpl(
    private val store: Settings,
    private val json: Json,
    private val autoStartManager: AutoStartManager,
) : SettingsRepository {

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun updateTheme(theme: AppTheme) {
        store.putString(KEY_THEME, theme.name)
        _settings.update { it.copy(theme = theme) }
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        store.putString(KEY_LANGUAGE, language.name)
        _settings.update { it.copy(language = language) }
    }

    override suspend fun setAutoStart(enabled: Boolean) {
        autoStartManager.setEnabled(enabled)
        store.putBoolean(KEY_AUTO_START, enabled)
        _settings.update { it.copy(autoStartEnabled = enabled) }
    }

    override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) {
        val current = _settings.value.excludedBrowserIds
        val updated = if (excluded) current + id else current - id
        if (updated == current) return
        store.putString(KEY_EXCLUSIONS, encodeIds(updated))
        _settings.update { it.copy(excludedBrowserIds = updated) }
    }

    private fun load(): AppSettings = AppSettings(
        theme = readEnum(KEY_THEME, AppTheme.entries, AppTheme.System),
        language = readEnum(KEY_LANGUAGE, AppLanguage.entries, AppLanguage.System),
        autoStartEnabled = store.getBoolean(KEY_AUTO_START, false),
        excludedBrowserIds = decodeIds(store.getStringOrNull(KEY_EXCLUSIONS)),
    )

    private fun <E : Enum<E>> readEnum(key: String, values: List<E>, default: E): E {
        val name = store.getStringOrNull(key) ?: return default
        return values.firstOrNull { it.name == name } ?: default
    }

    private fun encodeIds(ids: Set<BrowserId>): String =
        json.encodeToString(SetSerializer(String.serializer()), ids.map { it.value }.toSet())

    private fun decodeIds(raw: String?): Set<BrowserId> {
        if (raw.isNullOrEmpty()) return emptySet()
        return runCatching {
            json.decodeFromString(SetSerializer(String.serializer()), raw)
                .map(::BrowserId)
                .toSet()
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val KEY_THEME = "settings.theme"
        const val KEY_LANGUAGE = "settings.language"
        const val KEY_AUTO_START = "settings.autoStart"
        const val KEY_EXCLUSIONS = "settings.exclusions"
    }
}
