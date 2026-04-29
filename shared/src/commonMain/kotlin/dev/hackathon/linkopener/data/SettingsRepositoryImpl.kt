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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
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

    // Serializes every setter so concurrent calls can't interleave a
    // read-modify-write (e.g. two setBrowserExcluded calls with different
    // ids racing through `_settings.value` and one losing the other's
    // edit). In practice callbacks fire sequentially on the Main
    // dispatcher today, but the lock keeps the contract honest if anyone
    // ever calls a setter from a different scope.
    private val writeLock = Mutex()

    override suspend fun updateTheme(theme: AppTheme) = writeLock.withLock {
        store.putString(KEY_THEME, theme.name)
        _settings.update { it.copy(theme = theme) }
    }

    override suspend fun updateLanguage(language: AppLanguage) = writeLock.withLock {
        store.putString(KEY_LANGUAGE, language.name)
        _settings.update { it.copy(language = language) }
    }

    override suspend fun setAutoStart(enabled: Boolean) = writeLock.withLock {
        autoStartManager.setEnabled(enabled)
        store.putBoolean(KEY_AUTO_START, enabled)
        _settings.update { it.copy(autoStartEnabled = enabled) }
    }

    override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) = writeLock.withLock {
        val current = _settings.value.excludedBrowserIds
        val updated = if (excluded) current + id else current - id
        if (updated == current) return@withLock
        writeJson(KEY_EXCLUSIONS, updated.map { it.value }.toSet(), STRING_SET_SERIALIZER)
        _settings.update { it.copy(excludedBrowserIds = updated) }
    }

    override suspend fun setBrowserOrder(order: List<BrowserId>) = writeLock.withLock {
        if (order == _settings.value.browserOrder) return@withLock
        writeJson(KEY_BROWSER_ORDER, order.map { it.value }, STRING_LIST_SERIALIZER)
        _settings.update { it.copy(browserOrder = order) }
    }

    override suspend fun addManualBrowser(browser: Browser) = writeLock.withLock {
        val current = _settings.value.manualBrowsers
        if (current.any { it.applicationPath == browser.applicationPath }) return@withLock
        val updated = current + browser
        writeJson(KEY_MANUAL_BROWSERS, updated, BROWSER_LIST_SERIALIZER)
        _settings.update { it.copy(manualBrowsers = updated) }
    }

    override suspend fun removeManualBrowser(id: BrowserId) = writeLock.withLock {
        val current = _settings.value.manualBrowsers
        val updated = current.filterNot { it.toBrowserId() == id }
        if (updated.size == current.size) return@withLock
        writeJson(KEY_MANUAL_BROWSERS, updated, BROWSER_LIST_SERIALIZER)
        _settings.update { it.copy(manualBrowsers = updated) }
    }

    override suspend fun setRules(rules: List<UrlRule>) = writeLock.withLock {
        if (rules == _settings.value.rules) return@withLock
        writeJson(KEY_RULES, rules, RULE_LIST_SERIALIZER)
        _settings.update { it.copy(rules = rules) }
    }

    override suspend fun setShowBrowserProfiles(enabled: Boolean) = writeLock.withLock {
        if (enabled == _settings.value.showBrowserProfiles) return@withLock
        store.putBoolean(KEY_SHOW_PROFILES, enabled)
        _settings.update { it.copy(showBrowserProfiles = enabled) }
    }

    private fun load(): AppSettings = AppSettings(
        theme = readEnum(KEY_THEME, AppTheme.entries, AppTheme.System),
        language = readEnum(KEY_LANGUAGE, AppLanguage.entries, AppLanguage.System),
        autoStartEnabled = store.getBoolean(KEY_AUTO_START, false),
        excludedBrowserIds = readJson(KEY_EXCLUSIONS, emptySet(), STRING_SET_SERIALIZER)
            .mapTo(HashSet(), ::BrowserId),
        browserOrder = readJson(KEY_BROWSER_ORDER, emptyList(), STRING_LIST_SERIALIZER)
            .map(::BrowserId),
        manualBrowsers = readJson(KEY_MANUAL_BROWSERS, emptyList(), BROWSER_LIST_SERIALIZER),
        rules = readJson(KEY_RULES, emptyList(), RULE_LIST_SERIALIZER),
        showBrowserProfiles = store.getBoolean(KEY_SHOW_PROFILES, true),
    )

    private fun <E : Enum<E>> readEnum(key: String, values: List<E>, default: E): E {
        val name = store.getStringOrNull(key) ?: return default
        return values.firstOrNull { it.name == name } ?: default
    }

    /**
     * Reads a JSON-serialized [T] under [key]. Returns [default] when the key
     * is absent / empty AND when the stored payload fails to decode (corrupt
     * JSON, schema drift, …) — corrupt persisted state should never crash
     * the app, just fall back to defaults.
     */
    private fun <T> readJson(key: String, default: T, serializer: KSerializer<T>): T {
        val raw = store.getStringOrNull(key)
        if (raw.isNullOrEmpty()) return default
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(default)
    }

    private fun <T> writeJson(key: String, value: T, serializer: KSerializer<T>) {
        store.putString(key, json.encodeToString(serializer, value))
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

        // BrowserId is a value class around String — we persist the bare
        // string so the JSON shape is `["a","b"]`, not `[{"value":"a"},…]`.
        // Held as singletons so each writer doesn't allocate a fresh
        // ListSerializer/SetSerializer wrapper per call.
        val STRING_SET_SERIALIZER: KSerializer<Set<String>> = SetSerializer(String.serializer())
        val STRING_LIST_SERIALIZER: KSerializer<List<String>> = ListSerializer(String.serializer())
        val BROWSER_LIST_SERIALIZER: KSerializer<List<Browser>> = ListSerializer(Browser.serializer())
        val RULE_LIST_SERIALIZER: KSerializer<List<UrlRule>> = ListSerializer(UrlRule.serializer())
    }
}
