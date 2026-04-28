# Стадия 4.1 — Default-browser flow + подключение реального discovery

Полировка экрана настроек после стадий 4.5 (визуал) и 2 (browser discovery от коллеги). Хронологически после 4.5, нумерация 4.1 — авторская причуда.

## Цели

1. Подключить **реальный список браузеров** из `DiscoverBrowsersUseCase` в Settings → секция Browser exclusions. Mock'и удалить.
2. Показать **баннер сверху** окна настроек, если приложение **не является дефолтным браузером**. С CTA-кнопкой «открыть системные настройки».
3. Добавить **отдельный пункт «Default browser»** в сайдбар с per-OS инструкцией как сделать приложение дефолтным.
4. **Переделать сайдбар**: клик показывает в правой панели только выбранный раздел, не скроллит большой список.

## Не входит

- Реальная **регистрация** приложения как браузера (`Info.plist`, registry, `.desktop`) — это стадия 3.
- Реальная **детекция** «являюсь ли я дефолтным» — детектить бессмысленно пока не зарегистрирован. До стадии 3 `isDefaultBrowser()` всегда возвращает `false`. Контракт готов; impl приедет позже.
- Изменения в коде стадии 2 (browser discovery) — не лезем, только используем контракт.

## Архитектура изменений

### Платформа (`shared/commonMain/platform/`)

```kotlin
interface DefaultBrowserService {
    suspend fun isDefaultBrowser(): Boolean
    val canOpenSystemSettings: Boolean
    suspend fun openSystemSettings(): Boolean // true on launch success
}
```

JVM implementations (`shared/jvmMain/platform/`):

- **`MacOsDefaultBrowserService`** — `isDefaultBrowser()` пока возвращает `false` (TODO: Launch Services через JNA когда доедет стадия 3). `openSystemSettings()` запускает `open x-apple.systempreferences:com.apple.preference.general` — открывает System Settings → General, где user сам доберётся до Default web browser.
- **`WindowsDefaultBrowserService`** — `isDefaultBrowser()` → false. `openSystemSettings()` → `cmd /c start ms-settings:defaultapps`.
- **`LinuxDefaultBrowserService`** — оба метода no-op'ы (`canOpenSystemSettings = false`). UI покажет только текстовую инструкцию.

`PlatformFactory.createDefaultBrowserService()` выбирает по `os.name`.

### Use cases (`shared/commonMain/domain/usecase/`)

```kotlin
class IsDefaultBrowserUseCase(service): suspend operator fun invoke(): Boolean
class OpenDefaultBrowserSettingsUseCase(service): suspend operator fun invoke(): Boolean
class GetCanOpenSystemSettingsUseCase(service): operator fun invoke(): Boolean  // sync, для UI чтобы скрыть кнопку на Linux
```

### ViewModel (`shared/commonMain/ui/settings/`)

Новые поля в `SettingsViewModel`:
- `browsers: StateFlow<BrowsersState>` где `BrowsersState = Loading | Loaded(List<Browser>) | Error(message)`
- `isDefaultBrowser: StateFlow<Boolean>`
- `canOpenSystemSettings: Boolean`
- `refreshBrowsers()`, `recheckDefaultBrowser()`, `openSystemSettings()` — все через `scope.launch`

В `init` запускаем discovery + проверку дефолта параллельно.

### UI (`shared/commonMain/ui/settings/SettingsScreen.kt`)

**Layout:**
- Top bar (как было)
- Tonal warning banner — показывается только если `!isDefaultBrowser`. Содержит icon + текст «Link Opener is not your default browser» + кнопку `Open settings`. Tonal (`errorContainer` или `tertiaryContainer`), не destructive.
- Под баннером: Row(Sidebar | ActiveSectionContent)
- Sidebar: 5 пунктов: Default browser / Appearance / Language / System / Browser exclusions
- ActiveSectionContent — показывает только выбранную секцию, fillMaxSize в правой панели, скролл внутри секции если контент длинный. **Никаких LazyColumn со всеми секциями.** `Crossfade(activeSection)` для плавного перехода.

**Новая секция `DefaultBrowserSection`:**
- Заголовок «Default browser»
- Status row: пиктограмма ✓ или ⚠ + строка «Currently default» / «Not the default browser».
- Карточка с **per-OS инструкцией** (4–6 коротких шагов). Текст из `Strings`, выбор по `currentOs`.
- Кнопка `Open System Settings` — disabled / hidden если `!canOpenSystemSettings`.
- Помечаем «Until packaging ships, the app cannot register itself; this section is informational» — заметка про текущую ограниченность.

**Обновлённая `ExclusionsSection`:**
- Принимает `BrowsersState` вместо мока
- `Loading`: spinner-row 64dp
- `Loaded(empty)`: text «No browsers detected» + retry button
- `Loaded(list)`: реальные строки. `BrowserId(browser.bundleId)` как ключ для exclusions storage. Display = `displayName` + `version?.let { " $it" }`. Initial = первая буква displayName. Accent — нейтральный (primaryContainer + primary text), без hash-палитры.
- `Error`: текст с ошибкой + retry button

### DI (`desktopApp/.../AppContainer.kt`)

- Добавляются: `defaultBrowserService`, `isDefaultBrowserUseCase`, `openDefaultBrowserSettingsUseCase`, `getCanOpenSystemSettingsUseCase`
- В `newSettingsViewModel()` прокидываются все use cases (включая существующий `discoverBrowsersUseCase`)
- Удаляется debug-`println` из `init` блока — VM сам триггерит discovery

### Strings (`shared/commonMain/ui/strings/`)

Добавляются ключи (en/ru):
- `bannerNotDefaultTitle`, `bannerNotDefaultBody`, `bannerOpenSettings`
- `sectionDefaultBrowser`
- `defaultBrowserStatusYes`, `defaultBrowserStatusNo`
- `defaultBrowserInstructionsHeader`, `defaultBrowserInstructionsMacOs`, `defaultBrowserInstructionsWindows`, `defaultBrowserInstructionsLinux`
- `defaultBrowserOpenSystemSettings`
- `defaultBrowserPackagingNote`
- `browsersLoading`, `browsersEmpty`, `browsersError`, `retry`

## Тесты

- Существующие 34 — должны остаться зелёными после переподключения VM-конструктора.
- Обновляем `SettingsViewModelTest`: даём фейк-`DiscoverBrowsersUseCase`, `IsDefaultBrowserUseCase`, `OpenDefaultBrowserSettingsUseCase`. Проверяем переходы `BrowsersState`, что `isDefaultBrowser` обновляется, что `openSystemSettings` зовёт сервис.
- Новых юнит-тестов на JVM-impl'ы `DefaultBrowserService` не пишем — там ProcessBuilder, тестировать отдельно нет смысла.

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :shared:jvmTest` — все тесты проходят.
- [ ] При запуске наверху окна виден баннер «not default browser».
- [ ] Клик «Open settings» на macOS открывает System Settings.
- [ ] Сайдбар показывает 5 пунктов; клик меняет правую панель (не скроллит).
- [ ] Раздел «Default browser» содержит per-OS инструкцию.
- [ ] Раздел «Browser exclusions» показывает Loading → Loaded(real list).
- [ ] Включение/выключение exclusion работает и сохраняется по bundleId.
- [ ] `MockBrowsers.kt` удалён.

## Чувствительные данные

Нет.

## Implementation notes (расхождения с тем, что в main)

Стадия закрыта в `a28402f Stage/04.1 default browser and discovery wiring (#3)`, далее доработана в `cd5fbef DefaultBrowserService: live observation via Flow + WatchService on macOS`. Что отличается от плана:

- **Live observation вместо разовой проверки.** План предлагал `IsDefaultBrowserUseCase` + `recheckDefaultBrowser()` по необходимости. На main работает `ObserveIsDefaultBrowserUseCase` поверх `DefaultBrowserService.observeIsDefaultBrowser(): Flow<Boolean>`. macOS-импл подписывается через `java.nio.file.WatchService` на изменение `~/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist` и эмитит свежий `isDefaultBrowser()` при каждом write. SettingsViewModel держит `StateFlow<Boolean>`, баннер «not default» прячется/появляется автоматически, без явных recheck-вызовов.
- `IsDefaultBrowserUseCase.kt` ещё лежит в `domain/usecase/`, но в графе DI больше не используется (см. `AppContainer`, `SettingsViewModel`). Можно безопасно удалить — отмечено в `TECHDEBT.md`.
- **`Crossfade(activeSection)`** заменён на обычное `when (activeSection)` в стадии 5: Crossfade кеширует per-targetState и не давал пересчитать строки при смене языка. Поведение визуально то же (моргания нет — сабтри маленькие).
- Acceptance criteria все выполнены, плюс баннер обновляется в realtime (без manual refresh) — это бонус относительно плана.
