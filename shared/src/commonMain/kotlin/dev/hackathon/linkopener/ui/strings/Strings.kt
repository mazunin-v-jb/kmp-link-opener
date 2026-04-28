package dev.hackathon.linkopener.ui.strings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme

data class Strings(
    val settingsTitle: String,
    val sectionAppearance: String,
    val sectionLanguage: String,
    val sectionSystem: String,
    val sectionBrowserExclusions: String,
    val themeLabel: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val languageLabel: String,
    val languageSystem: String,
    val startAtLogin: String,
    val startAtLoginDescription: String,
    val exclusionsPlaceholder: String,
    val exclusionsCurrentPrefix: String,
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
    settingsTitle = "Settings",
    sectionAppearance = "Appearance",
    sectionLanguage = "Language",
    sectionSystem = "System",
    sectionBrowserExclusions = "Browser exclusions",
    themeLabel = "Theme",
    themeSystem = "System",
    themeLight = "Light",
    themeDark = "Dark",
    languageLabel = "Interface language",
    languageSystem = "System",
    startAtLogin = "Start at login",
    startAtLoginDescription = "Launch the app automatically when you sign in.",
    exclusionsPlaceholder = "Available once browser discovery ships (stage 2).",
    exclusionsCurrentPrefix = "Currently excluded ids: ",
    trayMenuSettings = "Settings",
    trayMenuQuit = "Quit",
    trayWindowSettingsSuffix = " — Settings",
)

val RussianStrings: Strings = Strings(
    settingsTitle = "Настройки",
    sectionAppearance = "Оформление",
    sectionLanguage = "Язык",
    sectionSystem = "Система",
    sectionBrowserExclusions = "Исключения браузеров",
    themeLabel = "Тема",
    themeSystem = "Системная",
    themeLight = "Светлая",
    themeDark = "Тёмная",
    languageLabel = "Язык интерфейса",
    languageSystem = "Системный",
    startAtLogin = "Запускать при входе",
    startAtLoginDescription = "Автоматически запускать приложение при входе в систему.",
    exclusionsPlaceholder = "Появится после стадии обнаружения браузеров (этап 2).",
    exclusionsCurrentPrefix = "Сейчас исключены: ",
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
