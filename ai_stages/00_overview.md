# KMP Link Opener — мастер-план

Кроссплатформенный «открыватель ссылок». Работает на macOS, Windows и Linux, написан на Kotlin (Compose Multiplatform + Kotlin Multiplatform). Регистрируется в системе как браузер; при клике на ссылку показывает попап выбора браузера.

## Целевые платформы

- **macOS** — стадия 1 (приоритет)
- **Windows** — после macOS
- **Linux** — после Windows

Все три таргета компилируются в JVM (Compose Desktop). Используем единый source set `jvmMain` и runtime-детекцию ОС для выбора платформенных реализаций.

## Версии (используем те, что в `gradle/libs.versions.toml`)

- Kotlin **2.3.20**
- Compose Multiplatform **1.10.3**
- kotlinx-coroutines **1.10.2**
- kotlinx-serialization **1.10.0**
- multiplatform-settings **1.3.0**
- Material 3 (через Compose MP `material3`) — добавляется на стадии 1

## Архитектура

### Gradle-модули

- **`:shared`** — KMP-библиотека: вся бизнес-логика, UI-компоненты, платформенные интерфейсы и реализации. Тестируется без запуска приложения.
- **`:desktopApp`** — runnable Compose Desktop приложение: `main()`, инициализация трея, composition root. Зависит от `:shared`.

### Слои внутри `:shared`

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/
├── core/
│   ├── model/         # Browser, BrowserVersion, LinkType, OpenRule, AppTheme, AppLanguage
│   └── result/        # OperationResult, доменные ошибки
├── domain/
│   ├── repository/    # BrowserRepository, SettingsRepository, RulesRepository (interfaces)
│   └── usecase/       # DiscoverBrowsersUseCase, OpenLinkUseCase, GetSettingsUseCase, ...
├── data/
│   ├── settings/      # SettingsRepository impl на multiplatform-settings
│   └── rules/         # RulesRepository impl
├── platform/
│   ├── BrowserDiscovery.kt          # interface
│   ├── DefaultBrowserRegistrar.kt   # interface
│   ├── AutoStartManager.kt          # interface
│   └── LinkLauncher.kt              # interface
└── ui/
    ├── theme/         # MaterialTheme + светлая/темная/системная
    ├── strings/       # Compose Resources, ru/en
    ├── settings/      # SettingsScreen + ViewModel
    ├── picker/        # BrowserPickerPopup
    └── tray/          # TrayMenu (composable меню)

shared/src/jvmMain/kotlin/dev/hackathon/linkopener/
└── platform/
    ├── PlatformFactory.kt           # выбирает impl по os.name
    ├── macos/                       # MacOsBrowserDiscovery, MacOsLinkLauncher, ...
    ├── windows/                     # WindowsBrowserDiscovery, ...
    └── linux/                       # LinuxBrowserDiscovery, ...
```

### Слои внутри `:desktopApp`

```
desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/
├── Main.kt              # main(), application { ... } из Compose Desktop
├── AppContainer.kt      # ручной composition root (DI без фреймворков)
└── tray/
    └── TrayHost.kt      # обёртка над Compose Tray API + меню

desktopApp/src/jvmMain/resources/
└── icons/
    └── app_tray_icon.png  # TODO: заглушка, заменить на финальный дизайн
```

### Принципы

- **DI без фреймворков** — `AppContainer` собирает граф вручную; легко тестируется, не тянет лишних зависимостей.
- **ViewModel-as-class** на coroutines + StateFlow, без AndroidX. Compose-функции получают `StateFlow`/`SharedFlow` через `collectAsState`.
- **Платформенные интерфейсы** в `commonMain`, реализации в `jvmMain` через runtime-детекцию (`System.getProperty("os.name")`). Альтернатива — service loader, но для трёх платформ runtime-факт проще.
- **Compose Resources** для строк (i18n) и иконок (когда дойдём до стадии иконок).
- **Тесты** — `kotlin.test` + `kotlinx-coroutines-test` для use cases и репозиториев, моки платформенных интерфейсов в commonTest.

## Этапы

### Стадия 1 — Прототип (текущая)

Подробности: [`01_prototype/plan.md`](01_prototype/plan.md).

Скелет архитектуры; трей с заглушкой меню «Settings» / «Quit»; пустое окно настроек; DI; базовые тесты. После этой стадии остальные можно делать параллельно.

### Стадия 2 — Browser discovery (macOS)

- `MacOsBrowserDiscovery` через Launch Services (`lsregister -dump` или JNI/JNA к `LSCopyAllHandlersForURLScheme`).
- Парсинг версии браузера (Info.plist приложения).
- UI настроек: список браузеров (read-only).
- **Параллелится с** стадиями 4, 5 после стабилизации `BrowserRepository`.

### Стадия 3 — Открытие ссылок (macOS)

- Регистрация приложения как URL-handler (Info.plist + `LSHandlerRank`).
- Получение URL при запуске через стандартные macOS API (Apple Events / `Desktop.setOpenURIHandler`).
- `BrowserPickerPopup` (3 + «Show all»).
- `MacOsLinkLauncher` через `open -a "Browser.app" URL` или Process API.

### Стадия 4 — Persistence настроек

- `SettingsRepository` на multiplatform-settings: тема, язык, autostart, исключения, кастомные правила (как JSON через kotlinx-serialization).
- UI: галочки/комбобоксы на экране настроек.
- **Параллелится с** стадиями 2, 5.

### Стадия 5 — Темы и i18n

- `AppTheme` (system/light/dark) на основе `isSystemInDarkTheme()` + явный override.
- Compose Resources: `strings.xml` ru/en, smooth-switch без рестарта.
- **Параллелится с** стадиями 2, 4.

### Стадия 6 — Кастомные правила

- DSL/UI «открывать `*.example.com` в Browser X».
- `RulesRepository` поверх `SettingsRepository`.
- Интеграция в `OpenLinkUseCase`: правила имеют приоритет над попапом.

### Стадия 7 — Поддержка Windows

- `WindowsBrowserDiscovery` через registry (`HKLM\SOFTWARE\Clients\StartMenuInternet`, `HKLM\SOFTWARE\RegisteredApplications`).
- `WindowsDefaultBrowserRegistrar` (см. ручные шаги).
- `WindowsAutoStartManager` через `HKCU\...\Run` или Startup-папку.
- `WindowsLinkLauncher` через `Process` + путь к exe.

### Стадия 8 — Поддержка Linux

- `LinuxBrowserDiscovery` парсит `/usr/share/applications/*.desktop` и `~/.local/share/applications/*.desktop`.
- `LinuxDefaultBrowserRegistrar` через `xdg-mime default` + установка собственного `.desktop`.
- `LinuxAutoStartManager` через `~/.config/autostart/`.
- `LinuxLinkLauncher` через `Process` + `Exec=` из `.desktop`.

## Параллелизация работы агентами

После стадии 1 граф зависимостей:

```
Стадия 1 (прототип, последовательная)
    ├── Стадия 2 (browsers macOS)  ─┐
    ├── Стадия 4 (settings)        ─┼── Стадия 3 (link opening)
    ├── Стадия 5 (themes/i18n)     ─┤
    │                               └── Стадия 6 (rules)
    ├── Стадия 7 (Windows)
    └── Стадия 8 (Linux)
```

- **Параллельно после стадии 1:** агенты на 2, 4, 5 (договорились по интерфейсам, моки доступны).
- **После 2 + 4:** стадии 3 и 6.
- **После 3:** стадии 7 и 8 параллельно (платформо-зависимая работа).

## Платформенные «ручные» шаги

Список пополняется по мере прохождения стадий. На стадии 1 нужно только запустить.

### macOS

- [ ] **Apple Developer ID + нотаризация** — чтобы macOS не блокировал «открыватель» из-за Gatekeeper. Без этого приложение не появится в системном списке браузеров через нормальный workflow и пользователю придётся вручную разрешать запуск.
- [ ] **Info.plist:** `CFBundleURLTypes` для `http`/`https`/`mailto` + `LSHandlerRank` (`Default` или `Owner`).
- [ ] **Sandbox / entitlements** — если будет распространяться через Mac App Store (опционально).

### Windows

- [ ] **Регистрация в реестре** как «Registered Application»: `HKLM\SOFTWARE\RegisteredApplications`, `HKLM\SOFTWARE\Clients\StartMenuInternet\<AppName>` с `Capabilities`.
- [ ] **Code signing** сертификат (Microsoft не блокирует, но SmartScreen ругается без него).
- [ ] **Установщик** (MSI/NSIS), который пишет ключи реестра — Compose Desktop умеет MSI через `Distributable`.

### Linux

- [ ] Установка `.desktop` файла с `MimeType=x-scheme-handler/http;x-scheme-handler/https;...`.
- [ ] `update-desktop-database` после установки.
- [ ] Опционально — `.deb`/`.rpm`/AppImage пакеты.

### Иконка

- [ ] **TODO:** заменить заглушечный `app_tray_icon.png` на финальный дизайн (нужны иконки разных размеров для трея каждой платформы; macOS — template image, Windows — `.ico`, Linux — `.png` нескольких размеров).

## Тестирование

- Каждая стадия добавляет unit-тесты для своих use cases и репозиториев.
- Платформенные реализации тестируются на соответствующей ОС (на CI — matrix).
- При каждом изменении: `./gradlew :shared:test :desktopApp:test`.
