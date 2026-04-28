# Стадия 4.5 — Применение дизайн-системы

Промежуточная стадия между 4 (settings persistence + базовый UI) и 5 (i18n полировка). Перерисовывает экран настроек под полноценную дизайн-систему от пользователя; заменяет placeholder-иконку трея на брендовую.

## Источники

- `~/Desktop/design/link_opener_icon/` — `icon.png` (брендовая иконка) + `DESIGN.md`.
- `~/Desktop/design/stitch_link_opener_settings_design/link_opener_design_system/DESIGN.md` — palette, typography, spacing, components.
- `~/Desktop/design/stitch_link_opener_settings_design/link_opener_settings_*/screen.png` + `code.html` — мокапы и эталонная HTML-структура.

## Цель

После стадии 4.5 экран настроек должен:

1. Использовать полную палитру дизайн-системы (Material 3 light + dark) с точными hex'ами из спека.
2. Иметь layout: **top app bar** (бренд + close/help) + **sidebar 240dp** (4 пункта nav) + **scrollable main area** с Card-секциями.
3. Браузерный список — populated моком (Safari/Chrome/Firefox/Brave/Arc) с возможностью toggle (исключён/включён). Real data будет на стадии 2.
4. Брендовая иконка вместо `PlaceholderTrayIcon` — TODO снимаем.

## Не входит

- Реальный browser discovery (стадия 2).
- Inter font в комплект (использую системный default — отдельным TODO).
- macOS template-style monochrome tray icon (раскрашенный PNG ОК для прототипа).
- Подразделы внутри табов сайдбара (Appearance Theme/Density/Visual Behavior из dark-en мокапа). Будем рендерить плоско, без табов.

## Архитектура изменений

### Тема (`shared/commonMain/ui/theme/`)

- `LinkOpenerColors.kt` — `LightColorScheme` и `DarkColorScheme` через `lightColorScheme()`/`darkColorScheme()` с явными hex из `DESIGN.md`. Dark scheme выводится из spec'а: primary → `inverse-primary` (#b0c6ff), surface → `#121212` (per spec text), на onSurface — оттенок light.
- `LinkOpenerTypography.kt` — `androidx.compose.material3.Typography` с переопределёнными `headlineSmall` (24/32/W400), `titleMedium` (16/24/W500/0.15px), `bodyLarge` (16/24/W400/0.5px), `labelMedium` (12/16/W500/0.5px). FontFamily — `FontFamily.Default` (TODO заменить на Inter, когда добавим font-файлы).
- `LinkOpenerTheme.kt` — обёртка вокруг `MaterialTheme(colorScheme, typography, content)`.

### Иконки (`shared/commonMain/ui/icons/`)

`AppIcons.kt` с `ImageVector`-объектами для иконок интерфейса: `Palette`, `Translate`, `SettingsSuggest`, `BrowserUpdated`, `Search`, `Add`, `Close`, `Help`, `Settings`, `Public`, `Explore`, `Shield`, `OpenInBrowser`. SVG path data берётся из Material Symbols (Apache 2.0 — можно копировать). Каждая иконка маленькая, выписана через `materialIcon { ... }` builder.

### Экран (`shared/commonMain/ui/settings/`)

`SettingsScreen.kt` — полный rewrite:

- Корневая `Row { Sidebar(); MainContent() }` под `Column { TopAppBar(); Row {...} }`
- `TopAppBar`: высота 48dp, brand row (icon + "Link Opener"), trailing — IconButton(help) + IconButton(close).
- `Sidebar`: фикс 240dp ширина, surfaceContainerLow background, "Settings v0.1.0" header, 4 nav items с иконкой + label. Текущий выбранный (из state) подсвечен primary-tinted border справа + текст primary.
- `MainContent`: `verticalScroll`, max-width контента 720dp, секции:
  - **Appearance** — Card с dropdown "Theme Mode" → System/Light/Dark
  - **Language** — Card с dropdown "App Language" → System/English/Русский
  - **System** — Card с "Start at login" + описание + Switch
  - **Browser exclusions** — заголовок + "+ Add browser..." TextButton (no-op TODO), search field, Card-список из 5 mock'ов; первая Safari "System default", вторая Chrome `excluded`, остальные `included`. Click on row toggles exclusion (wired to `setBrowserExcludedUseCase`).

### Trayicon

- Удаляем `PlaceholderTrayIcon.kt` целиком.
- Копируем `icon.png` → `desktopApp/src/jvmMain/resources/icons/app_icon.png`.
- В `TrayHost.kt`: `Tray(icon = painterResource("icons/app_icon.png"))` — снимаем TODO.
- Тот же ресурс используется для `Window(icon = ...)` Settings-окна.

### Strings (`shared/commonMain/ui/strings/`)

Добавляем ключи: `appName` ("Link Opener"), `appVersion` ("v0.1.0"), `themeMode` / `Режим темы`, `appLanguage` / `Язык`, `addBrowser` / `Добавить браузер...`, `searchBrowsers` / `Поиск браузеров...`, `included` / `Включён`, `excluded` / `Исключён`, `systemDefault` / `Системный по умолчанию`, плюс кнопки aria-labels (help, close).

## Mock browser data

Захардкожен в `SettingsScreen.kt` пока стадия 2 не доехала. Каждый mock — `MockBrowser(id: BrowserId, displayName: String, secondaryLabel: String, accentColor: Color, iconLetter: String)`. **TODO: replace with `BrowserRepository.observe()` from stage 2.**

Mock'и:
- `safari` — "Safari 17.4", "System Default", оттенок голубой, `S`
- `chrome` — "Google Chrome 124.0.6367.62", "" (никакой подписи), `G`
- `firefox` — "Firefox 125.0.1", "Developer Edition", оранжевый, `F`
- `brave` — "Brave 1.65.114", "Privacy Focused", синий, `B`
- `arc` — "Arc 1.42", "Recent Session", тёмный, `A`

Toggle exclusion работает по-настоящему — пишет в `SettingsRepository.setBrowserExcluded(BrowserId)`.

## Тесты

- Существующие 34 — должны остаться зелёными (изменения чисто UI/визуал, бизнес-логика та же).
- Новых юнит-тестов не вводим (рендеринг проверяется глазами через smoke).

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :shared:jvmTest` — 34/34 как было.
- [ ] `./gradlew :desktopApp:run` — окно настроек выглядит как мокап `light_en_populated`/`dark_en_populated` (с допуском на отсутствие Inter).
- [ ] Брендовая иконка в трее, не circle-placeholder.
- [ ] `PlaceholderTrayIcon.kt` удалён, TODO про иконку трея — снят.
- [ ] Mock browser list — Chrome исключён по умолчанию, остальные включены; toggle работает и сохраняется в storage.
- [ ] Тёмная тема применяется (по факту настройки + системы).

## Чувствительные данные

Нет. Бренд-иконка — обычный PNG, в `.gitignore` не нуждается.
