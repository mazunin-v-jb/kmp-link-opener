# Bugfix: single-instance enforcement

## Проблема

Сейчас приложение можно запустить несколько раз — будь то двойным кликом по `.app`, через `./gradlew :desktopApp:run`, или из разных запусков IDE. Каждая копия регистрирует свой `Desktop.setOpenURIHandler`, поднимает свой tray-icon и держит свою композицию. На macOS `.app`-бандлы по умолчанию single-instance через LaunchServices, но при смешанных запусках (gradle + установленный .app) или на Linux/Windows эта гарантия не действует.

## Цель

Запуск второй копии:
- если **окно настроек закрыто** — primary открывает Settings;
- если **окно настроек уже открыто** — primary показывает мемный snackbar внутри Settings;
- secondary в любом случае завершается с кодом 0.

## Архитектура

Стандартный pattern для JVM-десктопа: **file lock + loopback socket**.

1. На старте `main()` пытаемся взять `FileChannel.tryLock()` на `~/.linkopener/instance.lock`.
2. Удалось → мы primary. Поднимаем `ServerSocket(0, …, InetAddress.loopback)`, записываем номер порта в `~/.linkopener/instance.port`, запускаем daemon-thread `accept()` в цикле. Каждое подключение — это сигнал «активируйся».
3. Не удалось → мы secondary. Читаем порт из port-file, открываем сокет на `127.0.0.1:<port>`, шлём байт, выходим `exitProcess(0)`.

Кроссплатформенность бесплатная: `FileChannel.tryLock` под капотом — `fcntl` на macOS/Linux и `LockFileEx` на Windows. ОС снимает лок при гибели процесса (включая SIGKILL), так что висящих локов не будет. `ServerSocket` на loopback и `Path.of(System.getProperty("user.home"), ".linkopener")` тоже одинаково работают на трёх ОС.

Эскейп-хатча на «разрешить несколько копий» нет — пока не нужен.

## Активационный канал

`AppContainer`:
```kotlin
private val _activationRequests = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val activationRequests: SharedFlow<Unit> = _activationRequests.asSharedFlow()
fun requestActivation() { _activationRequests.tryEmit(Unit) }
```

`tryEmit` thread-safe — слушающий тред гарда дёргает его прямо из своего accept-loop.

## Развилка в TrayHost

TrayHostBody владеет `settingsAnchor` и заводит локальный `MutableSharedFlow<Unit>` для «нудж в Settings»:

```kotlin
val settingsNudges = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, ...) }

LaunchedEffect(container) {
    container.activationRequests.collect {
        if (settingsAnchor == null) {
            settingsAnchor = currentCursorPosition()
        } else {
            settingsNudges.tryEmit(Unit)
        }
    }
}
```

`settingsNudges` пробрасывается параметром в `SettingsScreen`.

## Snackbar внутри Settings

Сейчас `SettingsScreen` — это `Surface(Column(...))`. Оборачиваем содержимое в `Box(Modifier.fillMaxSize())`, существующая структура остаётся внутри, а на `Alignment.BottomCenter` вешаем `SnackbarHost(SnackbarHostState)`. Добавляем параметр:

```kotlin
fun SettingsScreen(
    ...,
    nudges: SharedFlow<Unit> = MutableSharedFlow(),  // дефолт — пустой поток
)
```

Внутри — `LaunchedEffect(nudges)` собирает и вызывает `snackbarHost.showSnackbar(message, withDismissAction = true, duration = Short)`.

Дефолтное значение нужно, чтобы существующие тесты `SettingsScreen` (если есть) продолжали компилиться.

## Мемный текст

Предлагаю на согласование:
- **EN:** `"Already here. I heard you the first time."`
- **RU:** `"Я тут. Услышал тебя ещё в первый раз."`

Альтернативы (на выбор):
- `"Стою. Жду. Не суетись."` / `"Standing here. Patient. Stop poking."`
- `"Ау, я здесь. Не надо меня тыкать."` (твоя формулировка) / `"Hey, I'm here. No poking required."`

Финальный текст — твой выбор; ключи ресурсов: `single_instance_already_running` или короче `nudge_already_running`.

## Файлы

**Новые:**
- `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/SingleInstanceGuard.kt`
- `desktopApp/src/jvmTest/kotlin/dev/hackathon/linkopener/app/SingleInstanceGuardTest.kt`
- `ai_stages/4-bugfixes/single_instance/plan.md` (этот файл)

**Изменения:**
- `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/Main.kt` — guard в начале, secondary → exit, primary → bind callback.
- `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/AppContainer.kt` — `activationRequests` + `requestActivation()`.
- `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/tray/TrayHost.kt` — collect activation, dispatch open vs nudge, передача `settingsNudges` в `SettingsScreen`.
- `shared/src/commonMain/kotlin/dev/hackathon/linkopener/ui/settings/SettingsScreen.kt` — новый параметр + Box + SnackbarHost.
- `shared/src/commonMain/composeResources/values/strings.xml` + `values-ru/strings.xml` — мем-ключ.

## Тесты

`SingleInstanceGuardTest` (jvmTest в `:desktopApp`):

1. **`acquire on empty dir → primary, release works`** — берём guard в `tempDir`, проверяем что не null, освобождаем.
2. **`second acquire while first held → null and signals primary`** — первый guard с `onActivationRequest = { latch.countDown() }`, второй вызов возвращает null, `latch.await(2s)` срабатывает.
3. **`stale port file → secondary still bails out cleanly`** — пишем мусорный порт в `instance.port`, вызываем `acquire`, никаких эксепшенов.
4. **`release → next acquire becomes primary`** — освобождаем первый guard, следующий вызов снова primary.

Дополнительно — при первом acquire запускается daemon-thread, который при `release()` должен прерваться (закрытием server socket). Тестим, что после release нет утечек — повторное открытие на том же порту не нужно (мы используем порт 0), но проверяем, что `Thread.activeCount` не растёт линейно по ходу 10 итераций acquire/release.

## Что вне scope

- Не передаём URL от secondary к primary. Если у пользователя как-то получилось запустить вторую копию с URL-аргументом (что само по себе редкость на маке — Launch Services роутит URL в живой instance), URL потеряется. Это можно добавить позже расширив протокол сокета (сейчас передаём один байт, перейдём на `\n`-terminated payload).
- Не делаем focus/raise существующего Settings-окна. `alwaysOnTop = true` уже стоит, окно всегда на виду; «вынырнуть» из-под других alwaysOnTop окон — отдельная задача.
- Не трогаем macOS LaunchServices-уровневое behaviour — guard работает поверх него, не вместо.

## Ветка

`bugfix/single-instance`. Коммит — один (с планом + кодом + тестами + строками), либо два (план отдельно). Без push.
