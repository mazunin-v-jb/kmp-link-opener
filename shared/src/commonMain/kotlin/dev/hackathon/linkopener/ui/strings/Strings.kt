package dev.hackathon.linkopener.ui.strings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.platform.HostOs

data class Strings(
    val appName: String,
    val versionPrefix: String,
    val settingsTitle: String,
    val sectionDefaultBrowser: String,
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
    // Default-browser banner + section
    val bannerNotDefaultTitle: String,
    val bannerNotDefaultBody: String,
    val bannerOpenSettings: String,
    val defaultBrowserStatusYes: String,
    val defaultBrowserStatusNo: String,
    val defaultBrowserInstructionsHeader: String,
    val defaultBrowserInstructionsMacOs: List<String>,
    val defaultBrowserInstructionsWindows: List<String>,
    val defaultBrowserInstructionsLinux: List<String>,
    val defaultBrowserOpenSystemSettings: String,
    val defaultBrowserPackagingNote: String,
    val defaultBrowserUnsupportedOs: String,
    // Browser list states
    val browsersLoading: String,
    val browsersEmpty: String,
    val browsersErrorPrefix: String,
    val retry: String,
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

    fun defaultBrowserInstructions(os: HostOs): List<String> = when (os) {
        HostOs.MacOs -> defaultBrowserInstructionsMacOs
        HostOs.Windows -> defaultBrowserInstructionsWindows
        HostOs.Linux -> defaultBrowserInstructionsLinux
        HostOs.Other -> listOf(defaultBrowserUnsupportedOs)
    }
}

val EnglishStrings: Strings = Strings(
    appName = "Link Opener",
    versionPrefix = "v",
    settingsTitle = "Settings",
    sectionDefaultBrowser = "Default browser",
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
    bannerNotDefaultTitle = "Link Opener isn't your default browser",
    bannerNotDefaultBody = "Set Link Opener as the default to intercept links opened from any app.",
    bannerOpenSettings = "Open settings",
    defaultBrowserStatusYes = "Link Opener is currently the default browser.",
    defaultBrowserStatusNo = "Link Opener is not the default browser.",
    defaultBrowserInstructionsHeader = "How to make Link Opener your default",
    defaultBrowserInstructionsMacOs = listOf(
        "Click the button below to open System Settings → General.",
        "Scroll to the “Default web browser” dropdown.",
        "Pick Link Opener from the list.",
    ),
    defaultBrowserInstructionsWindows = listOf(
        "Click the button below to open Settings → Apps → Default apps.",
        "Find “Web browser” in the list of categories.",
        "Click the current default and select Link Opener.",
    ),
    defaultBrowserInstructionsLinux = listOf(
        "Open a terminal.",
        "Run: xdg-settings set default-web-browser link-opener.desktop",
        "Or use your distribution's default-applications manager (e.g., GNOME Control Center → Default Applications).",
    ),
    defaultBrowserOpenSystemSettings = "Open System Settings",
    defaultBrowserPackagingNote = "Until Link Opener is installed via a packaged build, the OS will not list it as a browser candidate. You can prepare the steps now and finish them after the next packaging release.",
    defaultBrowserUnsupportedOs = "This operating system isn't supported yet.",
    browsersLoading = "Scanning installed browsers…",
    browsersEmpty = "No browsers detected on this system.",
    browsersErrorPrefix = "Browser discovery failed: ",
    retry = "Retry",
)

val RussianStrings: Strings = Strings(
    appName = "Link Opener",
    versionPrefix = "v",
    settingsTitle = "Настройки",
    sectionDefaultBrowser = "Браузер по умолчанию",
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
    bannerNotDefaultTitle = "Link Opener не выбран браузером по умолчанию",
    bannerNotDefaultBody = "Назначьте Link Opener браузером по умолчанию, чтобы перехватывать ссылки из любых приложений.",
    bannerOpenSettings = "Открыть настройки",
    defaultBrowserStatusYes = "Link Opener сейчас выбран браузером по умолчанию.",
    defaultBrowserStatusNo = "Link Opener не является браузером по умолчанию.",
    defaultBrowserInstructionsHeader = "Как сделать Link Opener браузером по умолчанию",
    defaultBrowserInstructionsMacOs = listOf(
        "Нажмите кнопку ниже — откроется «Системные настройки» → «Основные».",
        "Найдите выпадающий список «Веб-браузер по умолчанию».",
        "Выберите Link Opener из списка.",
    ),
    defaultBrowserInstructionsWindows = listOf(
        "Нажмите кнопку ниже — откроется Параметры → Приложения → Приложения по умолчанию.",
        "Найдите категорию «Веб-браузер».",
        "Кликните по текущему браузеру и выберите Link Opener.",
    ),
    defaultBrowserInstructionsLinux = listOf(
        "Откройте терминал.",
        "Выполните: xdg-settings set default-web-browser link-opener.desktop",
        "Либо воспользуйтесь менеджером приложений по умолчанию вашего DE (например, GNOME Control Center → «Приложения по умолчанию»).",
    ),
    defaultBrowserOpenSystemSettings = "Открыть системные настройки",
    defaultBrowserPackagingNote = "Пока Link Opener не упакован в установщик, ОС не будет показывать его в списке кандидатов. Подготовьте шаги сейчас, а завершите после ближайшего релиза с пакетом.",
    defaultBrowserUnsupportedOs = "Эта операционная система пока не поддерживается.",
    browsersLoading = "Сканирую установленные браузеры…",
    browsersEmpty = "Браузеры в системе не найдены.",
    browsersErrorPrefix = "Ошибка обнаружения браузеров: ",
    retry = "Повторить",
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
