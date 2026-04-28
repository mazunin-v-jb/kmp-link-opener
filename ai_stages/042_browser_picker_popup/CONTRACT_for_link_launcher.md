# Контракт `LinkLauncher` — для агента коллеги (стадия 3b)

Стадия 4.2 (browser-picker popup) добавила в репо интерфейс `LinkLauncher` и stub-имплементацию `PrintingLinkLauncher`, которая просто пишет в stdout «would launch X with URL Y». Этого достаточно чтобы UI работал end-to-end в дев-режиме, но реальное открытие ссылки в выбранном браузере — задача стадии 3b.

Эта спека — то что нужно реализовать, чтобы `PickerCoordinator` после клика пользователя реально запускал нужный браузер с нужным URL'ом на каждой ОС.

## Где лежит контракт в коде

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/platform/LinkLauncher.kt
```

```kotlin
package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

interface LinkLauncher {
    suspend fun openIn(browser: Browser, url: String): Boolean
}
```

Параметры:
- `browser: Browser` — модель из `core.model`. Поля доступные для launcher'а:
  - `bundleId: String` (например `"com.apple.Safari"`)
  - `displayName: String` (`"Safari"`)
  - `applicationPath: String` (`"/Applications/Safari.app"` на macOS, `"C:\Program Files\..."` на Windows, путь к executable или `.desktop` на Linux)
  - `version: String?` — справочно, для запуска не нужно
- `url: String` — уже валидированная http/https ссылка из `JvmUrlReceiver`. Передавать as-is.

Возврат: `true` если процесс браузера успешно запустился; `false` на любую ошибку (путь не найден, exec не удался, и т.д.). Caller (`PickerCoordinator`) пока не использует возврат (открытие — fire-and-forget), но в будущих стадиях планируется toast/notification на ошибку.

## Где `LinkLauncher` используется

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/ui/picker/PickerCoordinator.kt
```

`pickBrowser(browser)` зовёт `launcher.openIn(browser, url)` в coroutine scope, потом сбрасывает state. То есть всё что должен сделать launcher — это запустить дочерний процесс и вернуться. Дочерний процесс может пережить наше приложение.

## Где launcher инстанциируется

```
shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/PlatformFactory.kt
```

Сейчас `createLinkLauncher()` всегда возвращает `PrintingLinkLauncher`. Нужно переписать в `when (currentOs)` стиле как сделано для `createBrowserDiscovery()` / `createDefaultBrowserService()`:

```kotlin
fun createLinkLauncher(): LinkLauncher = when (currentOs) {
    HostOs.MacOs -> MacOsLinkLauncher()
    HostOs.Windows -> WindowsLinkLauncher()
    HostOs.Linux -> LinuxLinkLauncher()
    HostOs.Other -> PrintingLinkLauncher()
}
```

И положить три реализации рядом:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/macos/MacOsLinkLauncher.kt`
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsLinkLauncher.kt`
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/linux/LinuxLinkLauncher.kt`

## Что должна делать каждая имплементация

### macOS

`open -a "<Browser.applicationPath>" -- "<url>"`

```kotlin
override suspend fun openIn(browser: Browser, url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ProcessBuilder("open", "-a", browser.applicationPath, "--", url)
            .inheritIO()
            .start()
            .waitFor()
        // По-хорошему проверить exit code; `open` обычно возвращает 0 даже если
        // фон-процесс упадёт, но это уже не наша проблема.
    }.isSuccess
}
```

Edge cases:
- Путь с пробелами — ProcessBuilder обрабатывает аргументы как массив, кавычки не нужны.
- URL со спецсимволами (`?`, `&`, `#`) — также передаётся как отдельный аргумент после `--`, шелл его не интерпретирует.
- `open -a` без `-n` повторно открывает существующий инстанс браузера, поднимая ОС вместо нового окна — это нужное поведение.

### Windows

`cmd.exe /c start "" "<browser executable or registered name>" "<url>"`

Здесь сложнее: путь до .exe браузера не всегда тот же что `applicationPath` который у коллеги в `MacOsBrowserDiscovery` (на Windows discovery пока заглушка). Когда стадия 7 добавит `WindowsBrowserDiscovery` через registry, она должна передавать через `applicationPath` путь к exe.

```kotlin
override suspend fun openIn(browser: Browser, url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ProcessBuilder("cmd.exe", "/c", "start", "", browser.applicationPath, url)
            .inheritIO()
            .start()
            .waitFor()
    }.isSuccess
}
```

Заметка про `start ""` — пустой первый аргумент это "title", иначе Windows интерпретирует следующий аргумент как title окна.

### Linux

Напрямую запустить executable. На Linux `applicationPath` будет либо путём к бинарю, либо к `.desktop` файлу — discovery (стадия 8) должен дать путь к executable.

```kotlin
override suspend fun openIn(browser: Browser, url: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ProcessBuilder(browser.applicationPath, url)
            .inheritIO()
            .start()
        // На Linux не ждём — некоторые браузеры (firefox) висят в foreground и
        // блокируют. Возвращаем true как только процесс стартовал.
        true
    }.getOrDefault(false)
}
```

## Тестирование

Существующая stub-имплементация (`PrintingLinkLauncher`) покрыта только косвенно через `PickerCoordinatorTest`. Для реальных launcher'ов:

- **Unit-тестирование** ProcessBuilder вызовов через DI (выносим конструктор `ProcessBuilder` в параметр):

```kotlin
class MacOsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { ProcessBuilder(it).inheritIO().start() },
) : LinkLauncher { ... }
```

И тест проверяет какие аргументы пришли в processFactory.

- **Smoke-test** на реальной ОС: открыть какой-нибудь URL в Safari, проверить что Safari действительно открылся с этим URL.

## Acceptance criteria для коллеги

- [ ] `MacOsLinkLauncher` запускает `open -a path -- url` и возвращает `true` на успех.
- [ ] `WindowsLinkLauncher` запускает `cmd /c start ...` и возвращает `true`.
- [ ] `LinuxLinkLauncher` запускает executable + url и возвращает `true`.
- [ ] `PlatformFactory.createLinkLauncher()` возвращает per-OS реализацию.
- [ ] `PrintingLinkLauncher` остаётся как fallback для `HostOs.Other` и для unit-тестов.
- [ ] Smoke-test: запустить приложение, кликнуть на тестовый URL через picker — Safari/Chrome открывается с этим URL.
- [ ] Юнит-тесты на argument construction для каждой ОС.

## Что НЕ нужно менять

- Сам интерфейс `LinkLauncher` — зафиксирован, я уже завязал на него UI.
- `PickerCoordinator`, `PickerWindow`, `BrowserPickerScreen`, `PrintingLinkLauncher` — это UI-сторона стадии 4.2.
- `Browser` model — уже стабилен после стадии 2.

## Перепроверка перед мержем

После того как launcher готов, прогон:
```bash
./gradlew :desktopApp:packageDmg
# install /Applications/Link Opener.app
# set as default browser in System Settings
# click http link in any app
# expect: picker -> click Safari -> Safari opens with that URL
```
