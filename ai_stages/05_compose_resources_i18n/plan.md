# Стадия 5 — Миграция строк на Compose Resources

Тема (system/light/dark) и i18n (en/ru) **уже работают** через hand-rolled `Strings` data class — добавлены ещё в стадии 4 polish. Эта стадия мигрирует строковую часть на стандартный механизм Compose Multiplatform Resources, чтобы было «солидно» и переводчики работали с XML напрямую.

Тема не трогается — `LinkOpenerTheme` + `resolveDarkMode` остаются как есть.

## Цель

После стадии 5:
- Строки UI лежат в `composeResources/values/strings.xml` (en, default) и `values-ru/strings.xml` (ru).
- Списки шагов (per-OS instructions) — в `string-array`.
- Все call site'ы пользуются `stringResource(Res.string.x)` / `stringArrayResource(Res.array.x)` напрямую, без посредника-data-class'а.
- При смене языка в Settings — переключение мгновенное, без рестарта.
- Старые `Strings.kt`, `StringsResolutionTest.kt` удалены.

## Что НЕ входит

- Per-tray-icon локализованные имена приложения (имя `Link Opener` остаётся одно и то же на всех языках).
- Поддержка `regions` (`en-US` vs `en-GB`) — у нас плоский язык.
- Plurals — пока ни одна строка не плюрализуется, добавим если понадобится.
- ICU MessageFormat для подстановок — везде где нужно склеивание используем `+` или `String.format`.

## Архитектура локали

**Проблема:** в Compose Multiplatform 1.10.3 `LocalComposeEnvironment` — `internal`, пользовательский CompositionLocal на локаль не пробросить. `stringResource()` внутри читает `androidx.compose.ui.text.intl.Locale.current` который на JVM мапится на `java.util.Locale.getDefault()`.

**Решение:** перед композицией с пользовательской локалью делаем `java.util.Locale.setDefault(...)` через `DisposableEffect(resolvedLocale)`, и оборачиваем контент в `key(resolvedLocale)` чтобы при смене локали сабтри пересоздалась.

```kotlin
val resolvedLocale = remember(settings.language, systemTag) {
    when (settings.language) {
        AppLanguage.En -> "en"
        AppLanguage.Ru -> "ru"
        AppLanguage.System -> systemTag.takeIf { it == "ru" || it == "en" } ?: "en"
    }
}

DisposableEffect(resolvedLocale) {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag(resolvedLocale))
    onDispose { Locale.setDefault(previous) }
}

key(resolvedLocale) {
    // контент использует stringResource(Res.string.X)
}
```

**Side-effect глобальный.** Установка `Locale.setDefault` влияет на весь JVM (DateFormat, NumberFormat, CollatorFactory). У нас нет таких зависимых потребителей, так что приемлемо.

## Файлы

### `shared/src/commonMain/composeResources/values/strings.xml` (en — default)

Все ключи из текущего `Strings.kt`. Нейминг snake_case как принято в Android-XML.

### `shared/src/commonMain/composeResources/values-ru/strings.xml` (ru)

Те же ключи с русскими значениями.

### `string-array` для инструкций default-browser

```xml
<string-array name="default_browser_instructions_macos">
    <item>Click the button below to open System Settings → Desktop &amp; Dock.</item>
    <item>Scroll to the "Default web browser" dropdown.</item>
    <item>Pick Link Opener from the list.</item>
</string-array>
```

Аналогично для `_windows`, `_linux`.

### Specials

- `app_name`, `language_english`, `language_russian` — не локализуются (фиксированные нативные формы), можно держать в strings.xml как single-locale значения. Compose Resources при отсутствии перевода берёт default.

## Замены call site'ов

### Было

```kotlin
Text(strings.settingsTitle)
strings.label(theme)  // when'ом
strings.defaultBrowserInstructions(currentOs)  // List<String>
```

### Станет

```kotlin
Text(stringResource(Res.string.settings_title))
themeLabel(theme)  // top-level @Composable функция, читает stringResource внутри
defaultBrowserInstructions(currentOs)  // top-level @Composable, читает stringArrayResource
```

Сигнатуры `SettingsScreen`, `BrowserPickerScreen`, etc. перестают принимать `strings: Strings` — все строки достаются из CompositionLocal.

## Удаления

- `shared/src/commonMain/.../ui/strings/Strings.kt` — целиком
- `shared/src/commonTest/.../ui/strings/StringsResolutionTest.kt` — больше не релевантен (run-time локаль определяется JVM, не нашей логикой)
- `resolveStrings(...)` — заменяется на `resolveLocale(...)` в TrayHost (private helper)

## Тесты

- Существующие 119 (после стадии 4.2) должны остаться зелёными после конструктор-рефакторов. Смогут — VM же не зависит от строк, тесты считают параметрами.
- `StringsLabelTest` — если он тестирует `Strings.label(theme)` / `Strings.label(language)`, эти функции переедут в @Composable helpers; такие тесты сложно писать без compose-ui-test setup'а — удалим.
- Новых тестов не пишем — XML это статический контент, проверяется глазами при запуске.

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :shared:jvmTest` — все оставшиеся тесты проходят.
- [ ] `./gradlew :desktopApp:run` — приложение стартует, settings работают на en + ru, переключение мгновенное.
- [ ] `Strings.kt` отсутствует.
- [ ] Test picker в трее — picker'е строки локализованы.
- [ ] Default-browser banner / instructions — локализованы.

## Чувствительные данные

Нет.
