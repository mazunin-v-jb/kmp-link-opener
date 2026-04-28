package dev.hackathon.linkopener.data

import com.russhwolf.settings.Settings
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.domain.repository.SettingsRepository
import dev.hackathon.linkopener.platform.AutoStartManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
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

    override suspend fun setBrowserOrder(order: List<BrowserId>) {
        if (order == _settings.value.browserOrder) return
        store.putString(KEY_BROWSER_ORDER, encodeOrder(order))
        _settings.update { it.copy(browserOrder = order) }
    }

    override suspend fun addManualBrowser(browser: Browser) {
        val current = _settings.value.manualBrowsers
        if (current.any { it.applicationPath == browser.applicationPath }) return
        val updated = current + browser
        store.putString(KEY_MANUAL_BROWSERS, encodeManual(updated))
        _settings.update { it.copy(manualBrowsers = updated) }
    }

    override suspend fun removeManualBrowser(id: BrowserId) {
        val current = _settings.value.manualBrowsers
        val updated = current.filterNot { it.toBrowserId() == id }
        if (updated.size == current.size) return
        store.putString(KEY_MANUAL_BROWSERS, encodeManual(updated))
        _settings.update { it.copy(manualBrowsers = updated) }
    }

    override suspend fun setRules(rules: List<UrlRule>) {
        if (rules == _settings.value.rules) return
        store.putString(KEY_RULES, encodeRules(rules))
        _settings.update { it.copy(rules = rules) }
    }

    override suspend fun setShowBrowserProfiles(enabled: Boolean) {
        if (enabled == _settings.value.showBrowserProfiles) return
        store.putBoolean(KEY_SHOW_PROFILES, enabled)
        _settings.update { it.copy(showBrowserProfiles = enabled) }
    }

    private fun load(): AppSettings = AppSettings(
        theme = readEnum(KEY_THEME, AppTheme.entries, AppTheme.System),
        language = readEnum(KEY_LANGUAGE, AppLanguage.entries, AppLanguage.System),
        autoStartEnabled = store.getBoolean(KEY_AUTO_START, false),
        excludedBrowserIds = decodeIds(store.getStringOrNull(KEY_EXCLUSIONS)),
        browserOrder = decodeOrder(store.getStringOrNull(KEY_BROWSER_ORDER)),
        manualBrowsers = decodeManual(store.getStringOrNull(KEY_MANUAL_BROWSERS)),
        rules = decodeRules(store.getStringOrNull(KEY_RULES)),
        showBrowserProfiles = store.getBoolean(KEY_SHOW_PROFILES, true),
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

    private fun encodeOrder(ids: List<BrowserId>): String =
        json.encodeToString(ListSerializer(String.serializer()), ids.map { it.value })

    private fun decodeOrder(raw: String?): List<BrowserId> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
                .map(::BrowserId)
        }.getOrDefault(emptyList())
    }

    private fun encodeManual(browsers: List<Browser>): String =
        json.encodeToString(ListSerializer(Browser.serializer()), browsers)

    private fun decodeManual(raw: String?): List<Browser> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Browser.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun encodeRules(rules: List<UrlRule>): String =
        json.encodeToString(ListSerializer(UrlRule.serializer()), rules)

    private fun decodeRules(raw: String?): List<UrlRule> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(UrlRule.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_THEME = "settings.theme"
        const val KEY_LANGUAGE = "settings.language"
        const val KEY_AUTO_START = "settings.autoStart"
        const val KEY_EXCLUSIONS = "settings.exclusions"
        const val KEY_BROWSER_ORDER = "settings.browserOrder"
        const val KEY_MANUAL_BROWSERS = "settings.manualBrowsers"
        const val KEY_RULES = "settings.rules"
        const val KEY_SHOW_PROFILES = "settings.showBrowserProfiles"
    }
}
