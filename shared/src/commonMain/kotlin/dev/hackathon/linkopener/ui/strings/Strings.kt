package dev.hackathon.linkopener.ui.strings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme

data class Strings(
    val appName: String,
    val versionPrefix: String,
    val settingsTitle: String,
    val sectionAppearance: String,
    val sectionLanguage: String,
    val sectionSystem: String,
    val sectionBrowserExclusions: String,
    val themeMode: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val appLanguage: String,
    val languageSystem: String,
    val startAtLogin: String,
    val startAtLoginDescription: String,
    val addBrowser: String,
    val searchBrowsers: String,
    val included: String,
    val excluded: String,
    val systemDefault: String,
    val emptyBrowsersMessage: String,
    val help: String,
    val close: String,
    val configure: String,
    val trayMenuSettings: String,
    val trayMenuQuit: String,
    val trayWindowSettingsSuffix: String,
) {
    fun label(theme: AppTheme): String = when (theme) {
        AppTheme.System -> themeSystem
        AppTheme.Light -> themeLight
        AppTheme.Dark -> themeDark
    }

    // Language names stay in their native form regardless of current UI
    // language — only "System" follows translation. This matches macOS /
    // browser conventions and avoids translation drift on every new locale.
    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.System -> languageSystem
        AppLanguage.En -> "English"
        AppLanguage.Ru -> "Русский"
    }
}

val EnglishStrings: Strings = Strings(
    appName = "Link Opener",
    versionPrefix = "v",
    settingsTitle = "Settings",
    sectionAppearance = "Appearance",
    sectionLanguage = "Language",
    sectionSystem = "System",
    sectionBrowserExclusions = "Browser exclusions",
    themeMode = "Theme Mode",
    themeSystem = "System",
    themeLight = "Light",
    themeDark = "Dark",
    appLanguage = "App Language",
    languageSystem = "System",
    startAtLogin = "Start at login",
    startAtLoginDescription = "Automatically launch Link Opener when you start your computer to keep it ready in the system tray.",
    addBrowser = "+ Add browser…",
    searchBrowsers = "Search browsers…",
    included = "Included",
    excluded = "Excluded",
    systemDefault = "System Default",
    emptyBrowsersMessage = "No installed browsers detected yet.",
    help = "Help",
    close = "Close",
    configure = "Configure",
    trayMenuSettings = "Settings",
    trayMenuQuit = "Quit",
    trayWindowSettingsSuffix = " — Settings",
)

val RussianStrings: Strings = Strings(
    appName = "Link Opener",
    versionPrefix = "v",
    settingsTitle = "Настройки",
    sectionAppearance = "Оформление",
    sectionLanguage = "Язык",
    sectionSystem = "Система",
    sectionBrowserExclusions = "Исключения браузеров",
    themeMode = "Режим темы",
    themeSystem = "Системная",
    themeLight = "Светлая",
    themeDark = "Тёмная",
    appLanguage = "Язык приложения",
    languageSystem = "Системный",
    startAtLogin = "Запускать при входе",
    startAtLoginDescription = "Автоматически запускать Link Opener при старте компьютера, чтобы он всегда был готов в трее.",
    addBrowser = "+ Добавить браузер…",
    searchBrowsers = "Поиск браузеров…",
    included = "Включён",
    excluded = "Исключён",
    systemDefault = "По умолчанию в системе",
    emptyBrowsersMessage = "Установленные браузеры пока не найдены.",
    help = "Помощь",
    close = "Закрыть",
    configure = "Настроить",
    trayMenuSettings = "Настройки",
    trayMenuQuit = "Выход",
    trayWindowSettingsSuffix = " — Настройки",
)

fun resolveStrings(language: AppLanguage, systemLanguageTag: String?): Strings = when (language) {
    AppLanguage.En -> EnglishStrings
    AppLanguage.Ru -> RussianStrings
    AppLanguage.System -> stringsForSystemTag(systemLanguageTag)
}

private fun stringsForSystemTag(tag: String?): Strings {
    val normalized = tag?.lowercase()?.substringBefore('-')?.substringBefore('_')
    return when (normalized) {
        "ru" -> RussianStrings
        else -> EnglishStrings
    }
}
