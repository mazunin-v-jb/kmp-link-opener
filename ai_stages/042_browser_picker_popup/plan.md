# Стадия 4.2 — Browser-picker popup

Когда наше приложение зарегистрировано как дефолтный браузер и пользователь кликает по ссылке в любом приложении, macOS роутит URL к нам через `Desktop.setOpenURIHandler`. Сейчас (стадия 3a) мы ловим URL и просто `println`'им. Нужен picker.

## Цель

При получении URL приложение спавнит **компактное окно поверх всех других** возле курсора со списком первых трёх установленных браузеров + кнопкой «Show all». Клик по строке открывает URL в выбранном браузере; клик вне окна — закрывает picker без открытия.

## Не входит

- **Реальный `LinkLauncher`** (имплементация — за коллегой по стадии 3b). Я даю контракт + stub'овую имплементацию которая `println`'ит «would launch X with Y». См. `CONTRACT_for_link_launcher.md`.
- Анимации появления/исчезновения picker'а.
- Drag-and-drop / hotkey shortcuts по списку.
- Группировка по приложениям с одинаковым именем (Safari Tech Preview vs Safari) — отображаются как есть из `BrowserRepository`.

## Зависимости со стадиями

- **2 + 3a + 4 (на main):** используем `DiscoverBrowsersUseCase`, `UrlReceiver`, `SettingsRepository`. Не модифицируем.
- **4.1 (отдельная ветка, не на main):** теоретически фильтрация исключённых браузеров через `excludedBrowserIds` опирается на UI стадии 4.1. Без 4.1 пользователь не может задать exclusions через UI, но storage уже на main → если стадия 4.1 смержится позже, фильтрация в picker'е заработает автоматически.
- **3b (контракт):** см. `CONTRACT_for_link_launcher.md` — что должен реализовать коллегин агент.

## Архитектура

### `LinkLauncher` (`shared/commonMain/platform/`)

```kotlin
interface LinkLauncher {
    suspend fun openIn(browser: Browser, url: String): Boolean
}
```

JVM реализации (`shared/jvmMain/platform/`):
- `PrintingLinkLauncher` — `println("[launch] would open $url in ${browser.displayName}")`. Для разработки/демо. **Помечен TODO**: подменить на реальный per-OS launcher (см. CONTRACT.md).
- Реальные `MacOsLinkLauncher` / `WindowsLinkLauncher` / `LinuxLinkLauncher` пишет коллега. PlatformFactory сейчас отдаёт `PrintingLinkLauncher`; когда коллега смержится — добавит свои в when-блоке.

### `PickerCoordinator` (`shared/commonMain/ui/picker/`)

State machine:
```kotlin
sealed interface PickerState {
    data object Hidden : PickerState
    data class Showing(val url: String, val browsers: List<Browser>) : PickerState
}

class PickerCoordinator(
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    private val getSettings: GetSettingsFlowUseCase,
    private val launcher: LinkLauncher,
    private val scope: CoroutineScope,
) {
    val state: StateFlow<PickerState>
    fun handleIncomingUrl(url: String)  // Hidden -> Showing
    fun pickBrowser(browser: Browser)   // Showing -> launches + Hidden
    fun dismiss()                        // Showing -> Hidden (no launch)
}
```

`handleIncomingUrl` запускает discovery, фильтрует по `settings.value.excludedBrowserIds`, выставляет `Showing(url, browsers)`. Если browsers.isEmpty() — всё равно Showing с пустым списком (UI покажет «No browsers configured» + ссылку в Settings).

Если новый URL приходит пока `Showing` — заменяет состояние (latest wins). Это нормальное поведение для browser-router'а.

### UI

**`BrowserPickerScreen`** (`shared/commonMain/ui/picker/`) — содержание окна picker'а:
- Header: "Open in:" + усечённый URL
- Список строк: иконка-плашка (буква на primaryContainer фоне) + displayName. Клик по строке = `onPick(browser)`.
- Изначально показано первых **3** строки.
- Если browsers.size > 3 → внизу TextButton "Show all (N)" — раскрывает остальные.
- Если browsers.isEmpty() → центральный текст «No browsers available» + line subtitle.
- Стиль: Material 3, dimensions ~320dp wide, height по контенту, rounded 12dp, surfaceContainerLow background.

**`PickerWindow`** (`desktopApp/jvmMain/app/tray/`) — Compose Window обёртка:
- `decoration = WindowDecoration.Undecorated()` — без рамки
- `transparent = true` — для скруглённых углов
- `alwaysOnTop = true`
- `focusable = true`
- `resizable = false`
- Position: текущая позиция курсора (`MouseInfo.getPointerInfo().location`)
- **Dismiss on focus loss**: внутри content `LaunchedEffect` подписывается на `LocalWindowInfo.current.isWindowFocused` через `snapshotFlow`; первое значение игнорируется (drop(1)), при последующем `false` зовёт `onDismiss`.
- ESC key dismiss: `onPreviewKeyEvent` ловит `Key.Escape` → `onDismiss`, returns true.

### Wiring

**`AppContainer`** добавляет:
```kotlin
private val linkLauncher = PlatformFactory.createLinkLauncher()
val pickerCoordinator = PickerCoordinator(
    discoverBrowsers = discoverBrowsersUseCase,
    getSettings = getSettingsFlowUseCase,
    launcher = linkLauncher,
    scope = coroutineScope,
)
```

**`Main.kt`** заменяет `println` callback'у URL receiver'а:
```kotlin
container.urlReceiver.start { url ->
    container.pickerCoordinator.handleIncomingUrl(url)
}
```

**`TrayHost`** добавляет рендеринг picker'а параллельно settings-окну:
```kotlin
val pickerState by container.pickerCoordinator.state.collectAsState()
when (val s = pickerState) {
    PickerState.Hidden -> Unit
    is PickerState.Showing -> PickerWindow(
        url = s.url,
        browsers = s.browsers,
        onPick = container.pickerCoordinator::pickBrowser,
        onDismiss = container.pickerCoordinator::dismiss,
    )
}
```

И в трей-меню — **dev-only пункт «Test picker»** для тестирования без packaging'а:
```kotlin
Item("Test picker (dev)", onClick = { container.pickerCoordinator.handleIncomingUrl("https://example.com") })
```
Помечен TODO: убрать до публичного релиза.

### Strings (`shared/commonMain/ui/strings/`)

Добавляются ключи (en/ru):
- `pickerHeaderOpen` ("Open in:" / "Открыть в:")
- `pickerShowAll(n)` ("Show all ($n)" / "Показать все ($n)")
- `pickerEmpty` ("No browsers available" / "Браузеры недоступны")
- `pickerEmptyHint` ("Open Settings to remove exclusions or install a browser." / ...)
- `trayMenuTestPicker` (dev-only)

## Тесты

`PickerCoordinatorTest` (commonTest):
- начальное состояние `Hidden`
- `handleIncomingUrl` переключает в `Showing(url, filteredBrowsers)`
- exclusions из settings отфильтрованы
- self-bundle отфильтрован (через DiscoverBrowsersUseCase которая уже это делает)
- `pickBrowser` зовёт `LinkLauncher.openIn`, очищает state в `Hidden`
- `dismiss` очищает state без вызова launcher'а
- если URL приходит при `Showing` — заменяет на новый

UI тесты не пишем (Compose UI testing не настроено).

## Acceptance criteria

- [ ] `./gradlew build` зелёный.
- [ ] `./gradlew :shared:jvmTest` — все тесты + новые PickerCoordinator проходят.
- [ ] Клик на «Test picker (dev)» в трее открывает picker возле курсора.
- [ ] Picker всегда поверх других окон.
- [ ] Клик на браузер вызывает `LinkLauncher.openIn(...)` (на stub'е → println), окно закрывается.
- [ ] Клик мимо окна — закрывает picker без launcher вызова.
- [ ] ESC закрывает picker.
- [ ] Если браузеров > 3 — видно «Show all (N)»; клик раскрывает полный список.

## Future work / Technical debt

### TD-1: заменить reflection в `MacOsAlwaysOnTopOverFullScreen` на устойчивое API

**Что есть сейчас.** `MacOsAlwaysOnTopOverFullScreen` достаёт `NSWindow*` из AWT
через reflection-цепочку в `sun.awt.AWTAccessor` → `sun.lwawt.LWWindowPeer` →
`sun.lwawt.macosx.CFRetainedResource.ptr`, потом зовёт `setLevel:` и
`setCollectionBehavior:` через JNA + libobjc. Требует:
- `--add-exports java.desktop/sun.awt=ALL-UNNAMED` (и для `sun.lwawt`,
  `sun.lwawt.macosx`)
- `--add-opens` тех же пакетов
- надежды на то, что Oracle не переименует поля во внутренних классах

**Проблема.** `sun.*` — internal API, не public. Любой JDK upgrade может
сломать reflection-цепочку без предупреждения. JEP 403 (Strongly Encapsulate
JDK Internals) идёт в сторону полной блокировки `--add-opens` для
internals. Сейчас уже warning «Restricted methods will be blocked in a
future release» в логах.

**Это не security-проблема** (mods bypass-flags открывают internals
**нашему же коду**, не атакующему), но maintainability-долг.

**Текущий fallback.** На любой reflection failure хелпер логирует ошибку и
тихо отказывает — picker всё равно показывается, просто на macOS
fullscreen-приложениях рендерится под ними как до фикса.

**Кандидаты на миграцию** (в порядке трудозатрат):

1. **[rococoa](https://github.com/iterate-ch/rococoa)** — типизированные Cocoa
   bindings для JVM. Спрятать reflection за библиотекой. Добавляет ~500 KB к
   дистрибутиву, но изолирует нашу кодобазу от изменений в JDK internals.
   Замена ~30 строк хелпера на ~5 строк типизированных вызовов.
2. **Свой нативный shim** — мини-библиотека на Objective-C, компилируется в
   `Picker-Helper.dylib`, ставится в bundle, зовётся через JNA по нормальному
   ABI без reflection. Самый стабильный путь, но добавляет cross-compile
   шаг в сборку (`xcrun clang -framework AppKit ...`).

**Acceptance для миграции:**
- [ ] Хелпер не использует reflection в `sun.*` пакеты
- [ ] `--add-exports` / `--add-opens` JVM args можно убрать (или хотя бы
      сократить до того что реально нужно остальному коду)
- [ ] Smoke-test на macOS Sonoma+/Sequoia: picker overlay'ит fullscreen
      Safari/Chrome/IDEA (та же проверка что у нас сейчас)
- [ ] Запустить на свежей версии JBR + Adoptium + Oracle JDK 21/24 чтобы
      убедиться что миграция совместима со всеми

**Когда делать:** перед публичным релизом / packaging'ом для
distribution. До тех пор reflection-вариант справляется и graceful fallback
делает его «безопасно ломающимся».

## Чувствительные данные

Нет.

## Ручные шаги для default-browser flow

1. `./gradlew :desktopApp:packageDmg` — собирает `Link Opener-1.0.0.dmg`.
2. Открыть DMG, перетащить .app в `/Applications`.
3. Запустить .app **хотя бы раз** — macOS Launch Services индексирует.
4. **System Settings → Desktop & Dock → Default web browser → Link Opener**.
5. Любой клик по `http://...` ссылке → JvmUrlReceiver → PickerCoordinator → picker.
