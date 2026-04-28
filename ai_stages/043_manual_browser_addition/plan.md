# Stage 043 — Manual browser addition

## Цель

Закрыть пункт спеки `prompts/1_Prompt.md:25`:

> Также там должна быть возможность добавления браузеров, которые могут открывать ссылки, но которые по каким-то причинам не были обнаружены самой системой.

То есть пользователь должен иметь возможность вручную ткнуть «вот ещё одно приложение, открывает ссылки, добавь его в список». Добавленные руками браузеры должны жить наравне с обнаруженными системой: появляться в списке Settings, в picker-попапе, уважать exclusions/order, переживать рестарт.

## В скоупе

- Кнопка `+ Add browser…` в `Settings → Browsers` секции (строка `add_browser` уже есть в `strings.xml` — placeholder).
- Native file picker (AWT `FileDialog` на macOS) → пользователь выбирает `.app`.
- Извлечение метаданных (`bundleId`, `displayName`, `version`) из `Info.plist` через существующий `InfoPlistReader`.
- Валидация: путь существует, это валидный `.app`-бандл, не наш собственный `dev.hackathon.linkopener`, не дубликат уже-известного пути.
- Persistence в `AppSettings.manualBrowsers`.
- Merge с discovered-списком в `BrowserRepositoryImpl` — discovered побеждает manual при конфликте по `applicationPath`.
- Кнопка `×` (или эквивалент) на строках manual-записей для удаления.
- Автотесты: модель, persistence, use cases, репозиторий, VM, extractor.
- Manual-тест-план для ручной проверки на macOS.

## Не в скоупе

- Windows/Linux file picker и metadata extractor — стабы (Stage 7/8 закроют).
- UI-индикация ошибок вроде «не валидный .app» — для MVP молча игнорируем неудачную попытку (use case возвращает sealed `AddResult`, который VM выбрасывает; видимое сообщение пользователю — отдельная задача после первого фидбека).
- Редактирование уже добавленного (изменение displayName/version) — добавил → можно только удалить и добавить заново.
- Отдельный «бэйдж» Manual / визуальное отличие manual-записей от discovered. Ровно одно отличие: на manual-строке видна кнопка `×`.

## Архитектура

### Модель данных

`AppSettings` приобретает четвёртое (после theme/language/autostart/exclusions/browserOrder) поле:

```kotlin
@Serializable
data class AppSettings(
    ...
    val manualBrowsers: List<Browser> = emptyList(),
)
```

`Browser` уже `@Serializable`-friendly (data class из примитивов и `String?`-version), отдельной аннотации не требуется — kotlinx.serialization умеет такие data class'ы из коробки, но добавить `@Serializable` на `Browser` всё равно стоит, чтобы list-сериализация работала декларативно.

### Persistence

`SettingsRepositoryImpl` сейчас хранит каждое поле под отдельным ключом. Добавим `KEY_MANUAL_BROWSERS` и сериализуем `List<Browser>` через `ListSerializer(Browser.serializer())`. Старые установки с пустым ключом загрузятся как `emptyList()`.

```kotlin
override suspend fun addManualBrowser(browser: Browser) { ... }
override suspend fun removeManualBrowser(id: BrowserId) { ... }
```

В интерфейсе `SettingsRepository` появятся два новых метода. `addManualBrowser` идемпотентный (вторая попытка с тем же `applicationPath` — no-op).

### Use cases

`AddManualBrowserUseCase` — берёт `path: String`, делегирует extract + валидирует + персистит:

```kotlin
class AddManualBrowserUseCase(
    private val extractor: BrowserMetadataExtractor,
    private val settings: SettingsRepository,
    private val ownBundleId: String,
) {
    suspend operator fun invoke(path: String): AddResult { ... }

    sealed interface AddResult {
        data class Added(val browser: Browser) : AddResult
        data object Duplicate : AddResult
        data object IsSelf : AddResult
        data class InvalidApp(val reason: String) : AddResult
    }
}
```

`RemoveManualBrowserUseCase` тривиален: `invoke(id)` → `settings.removeManualBrowser(id)`.

### Metadata extractor

```kotlin
// commonMain
interface BrowserMetadataExtractor {
    suspend fun extract(path: String): ExtractResult
    sealed interface ExtractResult {
        data class Success(val browser: Browser) : ExtractResult
        data class Failure(val reason: String) : ExtractResult
    }
}
```

`MacOsBrowserMetadataExtractor` (jvmMain) использует существующий `InfoPlistReader`:

- Проверяет, что `path` ведёт на существующий каталог с суффиксом `.app`.
- Читает `<path>/Contents/Info.plist`.
- Извлекает `CFBundleIdentifier` (= bundleId), `CFBundleName`/`CFBundleDisplayName` (= displayName, fallback на имя файла без `.app`), `CFBundleShortVersionString` (= version, optional).
- Возвращает `Success(Browser(...))` или `Failure(...)` с человекочитаемой причиной.

Stub-реализации для Win/Linux в `PlatformFactory` возвращают `Failure("Manual browser addition not yet supported on this platform")`. Stage 7/8 их заменят.

### `BrowserRepositoryImpl` merge

Сейчас репозиторий — тонкая обёртка над `BrowserDiscovery` с одним кэшем. Изменения:

```kotlin
class BrowserRepositoryImpl(
    private val discovery: BrowserDiscovery,
    private val settings: SettingsRepository,
) : BrowserRepository {
    private var cachedDiscovered: List<Browser>? = null
    override suspend fun getInstalledBrowsers(): List<Browser> {
        val discovered = cachedDiscovered ?: discovery.discover().also { cachedDiscovered = it }
        return mergeWithManual(discovered)
    }
    override suspend fun refresh(): List<Browser> {
        val discovered = discovery.discover().also { cachedDiscovered = it }
        return mergeWithManual(discovered)
    }
    private fun mergeWithManual(discovered: List<Browser>): List<Browser> {
        val manual = settings.settings.value.manualBrowsers
        val seen = discovered.map { it.applicationPath }.toSet()
        return discovered + manual.filterNot { it.applicationPath in seen }
    }
}
```

Дискавери-кэш живёт по-старому. Список `manualBrowsers` всегда читается «свежим» из `settings.value` — то есть после `addManualBrowser` следующий `getInstalledBrowsers()` уже видит новую запись без forced-refresh.

### UI

`SettingsScreen.kt` экспозит callback `onAddBrowserClick: () -> Unit` (открывает file-picker — реализация в `:desktopApp`) и `onRemoveManualBrowser: (BrowserId) -> Unit` (вызывает VM.onRemoveManualBrowser).

В `BrowserList` после поля поиска и перед списком — кнопка `+ Add browser…` (использует существующую строку `Res.string.add_browser`). Стиль — outlined button или просто текстовая ссылка.

В `BrowserRow` появляется опциональная иконка `×`: показывается, если строка соответствует записи из `manualBrowsers` (передаётся флаг `isManual: Boolean`). Клик → confirm-нет / просто удаление (для MVP без confirm).

### `:desktopApp` file picker

`Main.kt` имеет доступ к AWT `Frame`/`Window`. Для каждого открытия Settings создаётся `pickBrowserFile: () -> String?` callback:

```kotlin
val pickBrowserFile: () -> String? = {
    val dialog = FileDialog(parentFrame, "Choose a browser app", FileDialog.LOAD)
    dialog.directory = "/Applications"
    dialog.isVisible = true
    val name = dialog.file
    val dir = dialog.directory
    if (name != null) "$dir$name".removeSuffix("/").trimEnd('/') else null
}
```

`dialog.file` для `.app` (это директория-бандл) на macOS возвращает имя бандла. Передаём VM.

VM expose `onAddBrowserClick(path: String?)` — если path не null, запускает use case и обновляет список.

### Граф DI

`AppContainer` тянет:

- `BrowserMetadataExtractor` через `PlatformFactory.createBrowserMetadataExtractor()`.
- `AddManualBrowserUseCase` (extractor + settings + ownBundleId).
- `RemoveManualBrowserUseCase` (settings).
- `BrowserRepositoryImpl` теперь принимает `SettingsRepository`.

VM получает оба use case'а в конструкторе.

## Шаги реализации (порядок)

1. **`AppSettings.manualBrowsers` + `Browser` @Serializable.** Сборка зелёная.
2. **`SettingsRepository(.add/.remove)ManualBrowser` + impl + persistence ключ + тесты round-trip + idempotency.**
3. **Use cases** `AddManualBrowserUseCase`, `RemoveManualBrowserUseCase`, `BrowserMetadataExtractor` (interface) + macOS impl + tests.
4. **`PlatformFactory.createBrowserMetadataExtractor()` + stubs** для Win/Linux.
5. **`BrowserRepositoryImpl` merge + tests** (включая edge case: manual-запись с тем же path что и discovered → discovered побеждает).
6. **`AppContainer` proводка** новых зависимостей.
7. **VM:** `onAddBrowserClick(path: String?)` + `onRemoveManualBrowser(id)`. После add/remove — `loadBrowsers(forceRefresh = false)` (re-merge). Tests: добавление, удаление, дубликат, self-self.
8. **UI:** кнопка Add в `BrowserList`, `×` на manual-row в `BrowserRow`, callback пробрасывается через ExclusionsSection / SettingsScreen.
9. **`:desktopApp/Main.kt`:** AWT `FileDialog` подключение, передача picked path в VM.
10. **i18n:** `add_browser` уже есть; нужны `remove_manual_browser` (en/ru) для accessibility content description.
11. **`./gradlew build` + ручная проверка** по сценариям ниже.
12. **Update `ai_stages/00_overview.md`** — добавить строку «043 Manual browser addition ✅».

## Тест-план

### Автотесты

- `AppSettings`: `manualBrowsers = emptyList()` по умолчанию.
- `SettingsRepositoryImpl`:
  - `setBrowserOrder` → existing.
  - `addManualBrowser` пишет ключ, `_settings` обновляется, повторный вызов с тем же путём — no-op.
  - `removeManualBrowser` удаляет соответствующий путь.
  - Round-trip через store: при reconstruction repo читает manual-список из ключа.
  - Битый JSON в ключе → fall-back в `emptyList()`.
- `AddManualBrowserUseCase`:
  - Happy path → `Added(browser)`, browser попадает в settings.
  - Дубликат discovered: invalid? Use case **не** знает про discovered — это забота `BrowserRepositoryImpl.mergeWithManual` (где discovered перетирает manual). Use case проверяет дубликат только в `manualBrowsers`.
  - Дубликат manual → `Duplicate`.
  - Self-bundle (`ownBundleId`) → `IsSelf`.
  - Extractor вернул `Failure(reason)` → `InvalidApp(reason)`.
- `RemoveManualBrowserUseCase`:
  - Удаляет существующую запись.
  - Удаление несуществующего id — no-op (не падает).
- `MacOsBrowserMetadataExtractor`:
  - Path не существует → `Failure`.
  - Path не оканчивается на `.app` → `Failure`.
  - `Info.plist` отсутствует → `Failure`.
  - Полный happy path (с фейковым `PlutilRunner` через `InfoPlistReader`-fixture) → `Success` с правильными полями.
  - `CFBundleIdentifier` отсутствует в plist → `Failure`.
  - `CFBundleName` отсутствует → fallback на имя файла без `.app`.
- `BrowserRepositoryImpl`:
  - `getInstalledBrowsers()` сливает discovered + manual.
  - При совпадении по `applicationPath` discovered побеждает.
  - Manual без discovered → попадает в результат.
  - `refresh()` пересканивает discovered, но manual-список тот же.
- `SettingsViewModel`:
  - `onAddBrowserClick(null)` (file-picker отменён) — no-op.
  - `onAddBrowserClick(validPath)` → use case вызван → список обновлён.
  - `onRemoveManualBrowser(id)` → use case вызван → список обновлён.

### Ручные тест-кейсы

После `./gradlew :desktopApp:createDistributable` + переустановки `.app` в `/Applications/`:

#### TC-1. Базовое добавление

1. Установи на машину браузер, который наш discovery точно не находит. Если такого нет — возьми любую обычную `.app`-программу для теста (например, дублируй `/Applications/Safari.app` под именем `My Custom Safari.app` где-нибудь в `~/Desktop/`).
2. Запусти приложение. Открой Settings → Browsers.
3. Жми «`+ Add browser…`». Должен открыться нативный macOS file-picker, по умолчанию в `/Applications`.
4. Перейди в `~/Desktop/`, выбери `My Custom Safari.app`, OK.
5. Список Browsers должен обновиться — внизу появилась новая запись с правильным именем и (если plist содержит) версией.

#### TC-2. Persistence через рестарт

1. После TC-1 закрой приложение (Quit из трея).
2. Открой Settings заново. Manual-запись должна быть на месте.

#### TC-3. Появление в picker'е

1. После TC-1: с включённой manual-записью в System Settings → Default Web Browser → выбери Link Opener.
2. Из терминала: `open https://example.com`.
3. В picker-попапе manual-запись должна быть среди вариантов (порядок по `browserOrder`, в самом конце если порядок не задан).
4. Кликни на manual-запись → она должна попытаться открыть URL через `open -a <path>`. Для нашего теста: `My Custom Safari.app` (символ ссылка / копия настоящего Safari) должна открыть страницу.

#### TC-4. Удаление

1. В Settings → Browsers, найди manual-запись. У неё должна быть кнопка `×` (которой нет у discovered-записей).
2. Жми `×`. Запись пропадает из списка.
3. Закрой и открой приложение. Убедись что не вернулась.

#### TC-5. Дубликат discovered

1. Возьми путь к уже-discovered браузеру (`/Applications/Safari.app`).
2. В Settings → `+ Add browser…` → выбери `/Applications/Safari.app`.
3. Список не должен задвоиться — discovered Safari остаётся, никакой новой записи не появляется. Use case вернул `Duplicate` (silently игнорируется в MVP UI).

#### TC-6. Невалидный выбор

1. В Settings → `+ Add browser…` → выбери что-то-не-`.app` (например, текстовый файл).
2. Список не меняется. Use case вернул `InvalidApp(reason)` (silently игнорируется в MVP UI). Никаких падений / зависаний.

#### TC-7. Self-add (попытка добавить себя)

1. В Settings → `+ Add browser…` → выбери `/Applications/Link Opener.app` (нас самих).
2. Список не меняется. Use case вернул `IsSelf`. Без падений.

#### TC-8. Cancel file picker

1. Жми «`+ Add browser…`», в picker-е нажми Cancel.
2. Список не меняется. Никаких падений.

#### TC-9. Order и exclusions работают на manual-записях

1. После TC-1: исключи manual-запись (toggle `Excluded`). Открой picker через `open https://example.com` → manual-запись не должна показаться.
2. Верни Included. Подвинь её стрелочками вверх, чтобы стала первой. Picker должен показать её первой.
3. Закрой/открой приложение. Order сохранился.

## Implementation notes

(заполняется по ходу реализации, если что-то отклонится от плана)
