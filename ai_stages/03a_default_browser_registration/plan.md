# Стадия 3a — Регистрация как default browser + приём URL

## Цель

1. После пакетирования и установки `.app` в `/Applications` приложение появляется в System Settings → Default web browser.
2. Когда пользователь выбрал нас как default и кликнул на http(s)-ссылку из любого источника — приложение получает URL.
3. URL логируется в stdout через `println` (как в стадии 2 для discovery). `BrowserPickerPopup` и `MacOsLinkLauncher` — отдельная стадия 3b.
4. `.app` подписан Developer ID и нотаризован, чтобы Gatekeeper не мешал регистрации в Launch Services.

## Не входит в стадию 3a

- `BrowserPickerPopup` (UI выбора браузера) — стадия 3b.
- `MacOsLinkLauncher` (запуск выбранного браузера через `open -a`) — стадия 3b.
- `OpenLinkUseCase` (роутинг URL → правила → picker → launcher) — стадия 3b.
- Кастомные правила (стадия 6).

## Принятые решения

### D1. Где регистрируется обработчик URL

В `Main.kt`, **до** `application { ... }`. JDK-API `Desktop.getDesktop().setOpenURIHandler { ... }` работает на macOS через Apple Events и срабатывает как для cold-start (когда система запускает наше .app в ответ на клик), так и для hot URL'ов (когда .app уже запущено). Обработчик должен быть зарегистрирован максимально рано, иначе самые первые URL'ы потеряются.

### D2. Контракт для стадии 3b

В commonMain — `interface UrlReceiver { fun start(onUrl: (String) -> Unit) }`. JvmMain impl `JvmUrlReceiver` оборачивает `Desktop.setOpenURIHandler`. На стадии 3a callback просто `println`-ит. На стадии 3b callback будет вызывать `OpenLinkUseCase`. Контракт фиксируем в `PlatformFactory.createUrlReceiver()`.

### D3. `LSHandlerRank` = `Default`

Не `Owner` (эксклюзивный handler — конфликт с Safari/Chrome/Firefox), не `Alternate` (мы НЕ хотим быть «дополнительным» — мы хотим участвовать в Default Browser-выборе). `Default` — стандартное значение для конкурирующих браузеров. Поведение: показываемся в System Settings → Default web browser, но не претендуем на эксклюзивное владение.

### D4. Bundle ID = `dev.hackathon.linkopener`

Соответствует package root в коде. Должен быть стабильным — Launch Services кэширует регистрацию по bundle id, и менять его задним числом — это новый «другой» app для системы.

### D5. Подпись и нотаризация — без секретов в репо

**⚠️ Чувствительные данные.** Identity для подписи и Apple ID для нотаризации НЕ кладутся в проектные файлы под VCS.

**Как храним:**
- В `~/.gradle/gradle.properties` (глобальный, вне репо) — две строки:
  ```
  macos.signing.identity=Developer ID Application: <Your Name> (<TEAMID>)
  macos.notarization.profile=NOTARIZATION_PROFILE
  ```
- `NOTARIZATION_PROFILE` — это имя профиля в Keychain, созданного **один раз** командой:
  ```bash
  xcrun notarytool store-credentials NOTARIZATION_PROFILE \
      --apple-id <your-apple-id> \
      --team-id <TEAMID> \
      --password <app-specific-password>
  ```
  После этого `notarytool` достаёт всё из Keychain — никаких паролей в файлах.

**В `desktopApp/build.gradle.kts`** — читаем эти property через `findProperty(...)`. Если property не задан — подпись/нотаризация не выполняются (unsigned `.app` для локальной разработки можно установить через правый клик → Open).

**Compose plugin DSL для notarization** не поддерживает напрямую `--keychain-profile`. Решение: добавляем тонкую gradle-таску `notarizeDmg`, которая после `packageDmg` запускает `xcrun notarytool submit --keychain-profile $profile --wait` и `xcrun stapler staple`. Конфигурируется через ту же `macos.notarization.profile` property.

### D6. Цикл разработки изменился

Из-за того, что Info.plist живёт только в packaged `.app`, обычный `./gradlew :desktopApp:run` **не годится** для тестирования регистрации (он запускает JAR, не `.app`). Цикл теперь:

1. `./gradlew :desktopApp:createDistributable` — собирает `.app` без DMG/нотаризации (быстрее).
2. Заменить `/Applications/Link Opener.app` на свежесобранный (можно через симлинк или helper-таску `installLocally`).
3. Открыть `.app` (двойной клик, или `open "/Applications/Link Opener.app"`).
4. Тестовая ссылка: `open https://example.com` в терминале → должна попасть в наш handler.
5. Логи смотрим в Console.app фильтром по нашему bundle id, или через `log stream --predicate 'process == "Link Opener"' --info`.

В план зафиксировано как known overhead. Для итераций по бизнес-логике (которая не зависит от регистрации) `:desktopApp:run` остаётся рабочим.

## Изменения в репозитории

### Gradle

**`desktopApp/build.gradle.kts`** — добавить `nativeDistributions.macOS { ... }`:

- `bundleID = "dev.hackathon.linkopener"`
- `signing { ... }` — условно от `macos.signing.identity`
- `infoPlist { extraKeysRawXml = "..." }` — `CFBundleURLTypes` с `http`/`https` + `LSHandlerRank=Default`

Плюс кастомная gradle-таска `notarizeDmg` (зависит от `packageDmg`):
```kotlin
tasks.register<Exec>("notarizeDmg") {
    val profile = findProperty("macos.notarization.profile") as String?
        ?: throw GradleException("set macos.notarization.profile to the notarytool keychain profile name")
    dependsOn("packageDmg")
    val dmg = layout.buildDirectory.file("compose/binaries/main/dmg/Link Opener-1.0.0.dmg")
    commandLine(
        "xcrun", "notarytool", "submit", dmg.get().asFile.absolutePath,
        "--keychain-profile", profile, "--wait",
    )
}
tasks.register<Exec>("stapleDmg") {
    dependsOn("notarizeDmg")
    val dmg = layout.buildDirectory.file("compose/binaries/main/dmg/Link Opener-1.0.0.dmg")
    commandLine("xcrun", "stapler", "staple", dmg.get().asFile.absolutePath)
}
```

**`.gitignore`** — НЕ требует изменений (signing.properties в проектную папку не кладём, всё в `~/.gradle/gradle.properties`).

### Структура `:shared`

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/platform/
└── UrlReceiver.kt           # новый: interface UrlReceiver

shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/
└── JvmUrlReceiver.kt        # новый: использует Desktop.setOpenURIHandler
```

`PlatformFactory.kt` дополняется методом `createUrlReceiver(): UrlReceiver`.

### Структура `:desktopApp`

```
desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/
├── Main.kt                  # обновляется: container.urlReceiver.start { url -> println(...) }
└── AppContainer.kt          # добавляется urlReceiver
```

## Контракты

```kotlin
// commonMain
package dev.hackathon.linkopener.platform

fun interface UrlReceiver {
    fun start(onUrl: (String) -> Unit)
}

// jvmMain
package dev.hackathon.linkopener.platform

import java.awt.Desktop

class JvmUrlReceiver : UrlReceiver {
    override fun start(onUrl: (String) -> Unit) {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
        desktop.setOpenURIHandler { event -> onUrl(event.uri.toString()) }
    }
}
```

В `Main.kt`:

```kotlin
fun main() {
    val container = AppContainer()
    container.urlReceiver.start { url ->
        println("[URL] received: $url")
    }
    application {
        TrayHost(container = container, onExit = ::exitApplication)
    }
}
```

В `AppContainer`:

```kotlin
val urlReceiver: UrlReceiver = PlatformFactory.createUrlReceiver()
```

Info.plist дополнительные ключи (`extraKeysRawXml`):

```xml
<key>CFBundleURLTypes</key>
<array>
    <dict>
        <key>CFBundleURLName</key>
        <string>Web URL</string>
        <key>CFBundleURLSchemes</key>
        <array>
            <string>http</string>
            <string>https</string>
        </array>
        <key>LSHandlerRank</key>
        <string>Default</string>
    </dict>
</array>
```

## Тесты

- Юнит-тесты на `JvmUrlReceiver` сложно написать честно: он завязан на JDK `Desktop` API и на наличие AWT-окружения. Пропускаем; покрываем через ручной приёмочный сценарий.
- Контракт `UrlReceiver` тестируем косвенно — через факт того, что `JvmUrlReceiver` имплементирует его и `AppContainer` его вызывает (компиляция проверит).

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :desktopApp:createDistributable` собирает `.app` без ошибок.
- [ ] `./gradlew :desktopApp:packageDmg` собирает подписанный DMG (если signing identity задан).
- [ ] `./gradlew :desktopApp:notarizeDmg` отправляет на нотаризацию и дожидается успеха (если profile задан).
- [ ] Установленный в `/Applications` `.app` открывается без алертов Gatekeeper.
- [ ] В System Settings → Default web browser в выпадающем списке появляется «Link Opener».
- [ ] После выбора нас как default: `open https://example.com` в терминале → в логах приложения видна строка `[URL] received: https://example.com`.
- [ ] Quit из трея корректно завершает приложение (без зависших процессов).

## Ручные шаги (требуются от тебя)

- [ ] Один раз создать notarytool keychain profile: `xcrun notarytool store-credentials NOTARIZATION_PROFILE --apple-id <...> --team-id <...> --password <app-specific-password>`.
- [ ] В `~/.gradle/gradle.properties` добавить:
      ```
      macos.signing.identity=Developer ID Application: <Your Name> (<TEAMID>)
      macos.notarization.profile=NOTARIZATION_PROFILE
      ```
- [ ] Первый запуск `.app` для регистрации в Launch Services (двойной клик из `/Applications`).
- [ ] System Settings → Default web browser → выбрать «Link Opener».
- [ ] Проверить через `open https://example.com` что URL долетает.
