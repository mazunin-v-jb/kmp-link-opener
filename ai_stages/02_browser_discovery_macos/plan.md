# Стадия 2 — Browser discovery (macOS)

## Цель

Получить рабочее обнаружение установленных на macOS браузеров и отображение их (read-only) в окне настроек:

1. Стабилизировать контракт `BrowserRepository` так, чтобы стадии 3 (открытие ссылок) и 6 (правила) могли строиться поверх него и параллелиться.
2. Реализовать `MacOsBrowserDiscovery`, который возвращает список браузеров с именем, bundle id, путём к `.app` и версией.
3. Показать список браузеров в `SettingsScreen` (имя + версия), с состояниями loading / loaded / error.
4. Покрыть use case и парсер Info.plist юнит-тестами с фикстурами.

После стадии 2 стадии 3 и 6 могут рассчитывать на доступный список браузеров; стадии 4 и 5 продолжают идти параллельно (settings persistence, темы) — они не блокируются стадией 2.

## Не входит в стадию 2

- Открытие ссылок (`MacOsLinkLauncher`) и `BrowserPickerPopup` — это стадия 3.
- Регистрация приложения как URL-handler в системе — стадия 3.
- Иконки браузеров (извлечение `.icns` из `Contents/Resources/`) — для прототипа в списке только текст. Иконка → отдельный TODO.
- Persistence выбранных браузеров / исключений — стадия 4.
- Реализации для Windows и Linux (`WindowsBrowserDiscovery`, `LinuxBrowserDiscovery`) — стадии 7 и 8. На стадии 2 неподдерживаемые ОС возвращают пустой список с понятным сообщением в логе.

## Принятые решения

### D1. Способ обнаружения браузеров — filesystem scan + `plutil`

Сканируем стандартные директории приложений (`/Applications`, `~/Applications`, `/System/Applications`, `/System/Volumes/Preboot/Cryptexes/App/System/Applications`), для каждого `.app` читаем `Contents/Info.plist` через `plutil -convert json -o - <path>` и фильтруем по схемам в `CFBundleURLTypes`.

**Почему так:**
- Ноль новых зависимостей. JNA не тянем.
- Парсер Info.plist (чистый JSON-парсер) тестируется фикстурами без macOS на CI.
- Покрывает 95%+ реальных кейсов (Homebrew Cask, drag-and-drop из DMG, Mac App Store — всё кладёт `.app` в `/Applications`).
- Та же ментальная модель пригодится на стадии 8 для Linux (`.desktop` files).

**Известные ограничения, осознанно принимаемые:**
- Браузеры в нестандартных путях (`~/Downloads/Firefox.app`, dev-сборки в `/opt/`) не увидим. Лечится позже добавлением JNA-источника как fallback, объединяя результаты по `bundleId`.
- Default browser системы не определяется. Для стадии 2 не нужно (показываем все, не выделяем default). Если на стадии 3 понадобится — добираем точечно через `defaults read ~/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist` или один JNA-вызов `LSCopyDefaultHandlerForURLScheme`, без полной миграции на Launch Services.
- Discovery занимает 1–3 секунды на холодном запуске (N+1 spawn `plutil`). Митигируется параллельным запуском на `Dispatchers.IO` и Loading-state в UI + кэшем в репозитории.
- Symlinks (Safari в Cryptex-разделе) — резолвим через `Path.toRealPath()`, чтобы не дублировать запись.

### D2. Источник display name — `CFBundleDisplayName` → `CFBundleName` → имя `.app`

Fallback-порядок: `CFBundleDisplayName` (то, что macOS показывает в Finder) → `CFBundleName` → имя `.app` без расширения. Финальный fallback гарантирует, что `displayName` никогда не пустой.

### D3. Критерий «это браузер» — наличие `http` или `https` в `CFBundleURLTypes`

`.app` считается браузером, если в `CFBundleURLTypes` есть элемент со схемой `http` или `https` в `CFBundleURLSchemes` (case-insensitive). Покрывает Safari, Chrome, Firefox, Edge, Arc, Brave, Opera, Vivaldi, DuckDuckGo Browser.

Если на машине разработчика в списке окажется мусор (например, мессенджер, регистрирующий `https://` под deep linking) — ужесточим критерий до «обработчик и `http`, и `https`» в рамках стадии 2.

## Изменения в репозитории

### Gradle

Без изменений. `kotlinx-serialization-json` уже подключён в `commonMain` (см. стадию 1) — используем его для парсинга JSON-вывода `plutil`. `plutil` — встроенная утилита macOS, никаких артефактов в Gradle.

### Структура `:shared`

Добавляем (всё в пакете `dev.hackathon.linkopener`, как в стадии 1):

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/
├── core/
│   └── model/
│       └── Browser.kt                    # новый: data class Browser(...)
├── domain/
│   ├── repository/
│   │   └── BrowserRepository.kt          # новый: suspend fun getInstalledBrowsers(): List<Browser>
│   └── usecase/
│       └── DiscoverBrowsersUseCase.kt    # новый: операторный invoke
├── data/
│   └── BrowserRepositoryImpl.kt          # новый: обёртка над BrowserDiscovery + кэш в памяти
├── platform/
│   └── BrowserDiscovery.kt               # новый: interface BrowserDiscovery
└── ui/
    └── settings/
        ├── SettingsScreen.kt             # обновляется: вместо «coming soon» — список браузеров
        ├── SettingsViewModel.kt          # новый: StateFlow<SettingsUiState>
        └── SettingsUiState.kt            # новый: Loading / Loaded(browsers) / Error(message)

shared/src/jvmMain/kotlin/dev/hackathon/linkopener/
└── platform/
    ├── PlatformFactory.kt                # новый: createBrowserDiscovery() по os.name
    └── macos/
        ├── MacOsBrowserDiscovery.kt      # новый: оркестрирует AppBundleScanner
        ├── AppBundleScanner.kt           # новый: чисто файловая логика (testable)
        └── InfoPlistReader.kt            # новый: вызов plutil + парсинг JSON (testable)
```

### Структура тестов

```
shared/src/commonTest/kotlin/dev/hackathon/linkopener/
├── domain/usecase/
│   └── DiscoverBrowsersUseCaseTest.kt    # use case + фейк-репозиторий
└── data/
    └── BrowserRepositoryImplTest.kt      # репозиторий + фейк-discovery (проверка кэша)

shared/src/jvmTest/kotlin/dev/hackathon/linkopener/platform/macos/
├── InfoPlistReaderTest.kt                # фикстуры plist (Safari, Chrome, не-браузер) → Browser?
└── AppBundleScannerTest.kt               # tmp-каталог с фейковыми .app → ожидаемый список
```

`jvmTest` нужен потому, что `plutil` доступен только на macOS (или mock-нём `InfoPlistReader` в `jvmTest`, если CI крутится на Linux). Решение — см. блок «Тестирование» ниже.

## Контракты

```kotlin
// commonMain — core/model
package dev.hackathon.linkopener.core.model

data class Browser(
    val bundleId: String,           // "com.google.Chrome" — уникальный ключ
    val displayName: String,        // "Google Chrome"
    val applicationPath: String,    // "/Applications/Google Chrome.app"
    val version: String?,           // "131.0.6778.86" или null если не нашли
)

// commonMain — domain/repository
package dev.hackathon.linkopener.domain.repository

interface BrowserRepository {
    suspend fun getInstalledBrowsers(): List<Browser>
}

// commonMain — domain/usecase
package dev.hackathon.linkopener.domain.usecase

class DiscoverBrowsersUseCase(private val repo: BrowserRepository) {
    suspend operator fun invoke(): List<Browser> = repo.getInstalledBrowsers()
}

// commonMain — platform
package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser

interface BrowserDiscovery {
    suspend fun discover(): List<Browser>
}

// commonMain — data
package dev.hackathon.linkopener.data

class BrowserRepositoryImpl(
    private val discovery: BrowserDiscovery,
) : BrowserRepository {
    private var cached: List<Browser>? = null
    override suspend fun getInstalledBrowsers(): List<Browser> =
        cached ?: discovery.discover().also { cached = it }
}
```

`BrowserRepositoryImpl` кэширует результат на время жизни процесса — discovery нелетный, повторно ходить за тем же списком при каждом открытии Settings смысла нет. Инвалидация кэша (refresh) появится позже, когда понадобится; на стадии 2 — без кнопки refresh.

### `PlatformFactory` (jvmMain)

```kotlin
package dev.hackathon.linkopener.platform

object PlatformFactory {
    fun createBrowserDiscovery(): BrowserDiscovery {
        val os = System.getProperty("os.name").lowercase()
        return when {
            "mac" in os || "darwin" in os -> MacOsBrowserDiscovery(...)
            else -> EmptyBrowserDiscovery   // на стадии 2 — заглушка
        }
    }
}
```

`EmptyBrowserDiscovery` возвращает пустой список + лог `INFO: browser discovery not implemented for <os>`. На стадиях 7/8 заменяется на реальные имплементации.

### `MacOsBrowserDiscovery`

```kotlin
class MacOsBrowserDiscovery(
    private val scanner: AppBundleScanner,
    private val plistReader: InfoPlistReader,
    private val searchRoots: List<Path> = defaultSearchRoots(),
) : BrowserDiscovery {
    override suspend fun discover(): List<Browser> = withContext(Dispatchers.IO) {
        searchRoots
            .flatMap { scanner.findAppBundles(it) }
            .mapNotNull { plistReader.readBrowser(it) }
            .distinctBy { it.bundleId }
            .sortedBy { it.displayName.lowercase() }
    }
}

private fun defaultSearchRoots(): List<Path> = listOf(
    Path("/Applications"),
    Path(System.getProperty("user.home"), "Applications"),
    Path("/System/Applications"),
    Path("/System/Volumes/Preboot/Cryptexes/App/System/Applications"),  // Safari в новых macOS
)
```

`AppBundleScanner` — рекурсивный (но неглубокий, depth ≤ 2) поиск каталогов с расширением `.app`. Не ходит внутрь `.app` для поиска вложенных бандлов.

`InfoPlistReader.readBrowser(appPath: Path): Browser?`:
1. `plutil -convert json -o - <appPath>/Contents/Info.plist` → JSON.
2. Парсинг через `kotlinx.serialization.json` (уже в зависимостях).
3. Проверка: есть ли в `CFBundleURLTypes` элемент со схемой `http` или `https` (case-insensitive). Если нет — возвращает null.
4. Извлечение `bundleId` (`CFBundleIdentifier`), `displayName` (fallback порядок: `CFBundleDisplayName` → `CFBundleName` → имя `.app` без расширения), `version` (`CFBundleShortVersionString` → `CFBundleVersion` → null).
5. `applicationPath` — абсолютный путь к `.app`.

## DI / composition root

`AppContainer` обновляется:

```kotlin
class AppContainer {
    // существующее
    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    val getAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)

    // новое
    private val browserDiscovery: BrowserDiscovery = PlatformFactory.createBrowserDiscovery()
    private val browserRepository: BrowserRepository = BrowserRepositoryImpl(browserDiscovery)
    val discoverBrowsersUseCase = DiscoverBrowsersUseCase(browserRepository)
}
```

`SettingsScreen` принимает `discoverBrowsersUseCase` как параметр (или сам ViewModel создаётся в `AppContainer` и пробрасывается). Решение — ViewModel создаётся внутри `SettingsScreen` через `remember { SettingsViewModel(useCase) }`, потому что lifecycle Settings-окна совпадает с жизнью composable. `useCase` приходит из `AppContainer`.

## UI

`SettingsScreen` отображает три состояния:

- **Loading** — `CircularProgressIndicator` по центру.
- **Loaded(browsers)** — `LazyColumn` со списком: иконка-заглушка (квадратик) + display name + version (мелким шрифтом). Если `version == null`, показываем «Unknown version».
- **Error(message)** — текст ошибки + кнопка Retry, которая вызывает useCase повторно.

`SettingsViewModel`:

```kotlin
class SettingsViewModel(
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val state: StateFlow<SettingsUiState> = _state

    init { reload() }

    fun reload() {
        scope.launch {
            _state.value = SettingsUiState.Loading
            try {
                _state.value = SettingsUiState.Loaded(discoverBrowsers())
            } catch (t: Throwable) {
                _state.value = SettingsUiState.Error(t.message ?: "Unknown error")
            }
        }
    }
}
```

`scope` — пробрасывается извне (из `SettingsScreen`'s `rememberCoroutineScope()`), чтобы корутины отменялись при закрытии окна.

## Тестирование

### Уровень 1 — common (мокаемое, кросс-платформенное)

- **`DiscoverBrowsersUseCaseTest`** — проверяет, что use case прокидывает результат из репозитория. Фейк-репозиторий с фиксированным списком.
- **`BrowserRepositoryImplTest`** — проверяет кэширование: discovery вызывается один раз при двух последовательных запросах. Фейк-discovery со счётчиком.
- **`SettingsViewModelTest`** — `kotlinx-coroutines-test` + `TestScope`. Проверяет переходы состояний: Loading → Loaded; Loading → Error при исключении; reload сбрасывает в Loading.

### Уровень 2 — jvm (macOS-специфичное)

- **`InfoPlistReaderTest`** — фикстуры в `jvmTest/resources/plists/`:
  - `chrome.plist` (минимальный валидный браузерный Info.plist) → возвращает `Browser` с правильными полями.
  - `safari.plist` → ок.
  - `slack.plist` (есть `slack://`, нет `http`/`https`) → null.
  - `corrupted.plist` (битый XML) → null + лог.
  
  Тест вызывает `plutil` напрямую — на CI без macOS этот тест должен либо скипаться (`Assume.assumeTrue(isMacOs)`), либо мокаться. **Решение:** разделить `InfoPlistReader` на `PlistJsonParser` (чистый — парсит готовый JSON) и `PlutilRunner` (вызывает процесс). Тестируем `PlistJsonParser` фикстурами JSON (генерируем один раз через plutil и кладём в репо), `PlutilRunner` — smoke-тест на macOS, `Assume.assumeTrue`.

- **`AppBundleScannerTest`** — создаёт во временной директории фейковую структуру (`fake-app.app/Contents/Info.plist`), проверяет что `findAppBundles` находит её. Кросс-платформенно (просто работает с файлами).

- **Интеграционный smoke-тест** `MacOsBrowserDiscoveryTest` — на реальной macOS-машине запускается `discover()` и проверяется, что вернулся непустой список. `Assume.assumeTrue(isMacOs)`.

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :shared:jvmTest` все тесты проходят (на macOS — включая smoke-тесты; на других ОС — JVM-specific тесты скипаются с понятным сообщением).
- [ ] На macOS запуск `./gradlew :desktopApp:run` → клик по трею → Settings: видим список реально установленных браузеров (минимум: Safari + Chrome / Firefox / Brave / Arc — что есть на машине разработчика).
- [ ] Каждая запись содержит имя и версию (либо «Unknown version»).
- [ ] Loading-состояние видно при первом открытии Settings (если discovery занимает >100мс — а оно обычно занимает).
- [ ] Закрытие Settings не падает (корутины ViewModel отменяются корректно).
- [ ] На не-macOS платформах (если запустить на Linux/Windows): Settings показывает пустой список без падения, в логе — INFO про неподдерживаемую ОС.
- [ ] Контракты `BrowserRepository` и `Browser` зафиксированы и не требуют изменений для стадий 3/6 (проверяется ревью плана с учётом стадий 3 и 6).

## Ручные шаги

Для стадии 2 — нет. `plutil` — встроенная утилита macOS, всегда доступна. Никаких подписей / разрешений не требуется (читаем только Info.plist в `/Applications`, доступ публичный).

Появятся на стадии 3 (регистрация как URL-handler) и далее.
