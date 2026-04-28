# Stage 046 — Chromium browser profile detection

## Цель

Если у пользователя в Chrome (или другом Chromium-форке) заведено несколько профилей — Work, Personal, и т.д. — мы хотим показывать каждый профиль как **отдельную запись** в списке браузеров и в picker'е, а при открытии URL передавать `--profile-directory=...`, чтобы ссылка реально открывалась в нужном профиле.

Не из исходной спеки, но логичное продолжение «несколько версий одного браузера = разные записи»: разные профили — те же разные «инкарнации» браузера для пользователя.

## Зафиксированные дизайн-решения

После обсуждения — все «как я бы предложил»:

1. **Только Chromium-family в MVP.** Chrome, Edge, Brave, Vivaldi, Opera, Chromium. Firefox (`profiles.ini`) и Safari (CloudKit-opaque) — не сейчас.
2. **Один-профильные браузеры не дробим.** Если у Chrome только Default — показываем «Chrome» как было. Только при N≥2 разворачиваем «Chrome — Work» / «Chrome — Personal».
3. **Strict backward-compat.** После релиза старые `BrowserId` (без `#profile`) для браузеров, у которых теперь есть профильные записи, осиротеют — exclusions/order/rules для них не сматчатся, тихо проигнорируются. Пользователь перенастраивает. Документируем.
4. **MVP без аватарок** — только текст «Chrome — Work». Чтение PNG-файлов из `Avatars/` дир — отдельной задачей если попросят.
5. **Профили — first-class.** Каждый профиль это полноценный участник списка браузеров: можно исключать, сортировать, ссылаться в rules независимо.

## В скоупе

- `BrowserProfile` модель + расширение `Browser` полем `profile: BrowserProfile? = null` (default — для не-профильных записей и не-Chromium браузеров).
- `BrowserFamily` enum + детекция по `bundleId`.
- Per-bundleId map к user-data директории (Chrome → `~/Library/Application Support/Google/Chrome/`, Edge → `Microsoft Edge/`, etc.).
- `ChromiumProfileScanner` — читает `<userDataDir>/Local State`, парсит `profile.info_cache`, возвращает `List<BrowserProfile>`.
- `MacOsBrowserDiscovery` — после обычной discovery, для каждого Chromium-браузера с N≥2 профилями эмитит N записей вместо 1.
- `MacOsLinkLauncher` — для browsers с `profile != null` запускает с `--profile-directory=<id>`.
- UI rows (Settings + picker) показывают «Chrome — Work» если профиль есть.
- Автотесты на каждый слой.
- Manual TC.

## Не в скоупе

- Firefox / Safari profile detection. Архитектура не блокирует — добавление Firefox это +1 family + 1 scanner, без правок остального.
- Аватарки.
- Live watcher на `Local State` — refresh-кнопкой обновляется. Кто часто меняет профили в открытом приложении — кликнет refresh.
- Live-обновление при добавлении profile в браузере во время работы нашего .app (Chromium пишет Local State при изменениях, мы могли бы наблюдать, но это полировка).
- Soft-migration старых `BrowserId` записей. См. § Migration ниже — strict orphaning.

## Архитектура

### Модели

```kotlin
// core/model/BrowserProfile.kt
@Serializable
data class BrowserProfile(
    val id: String,           // dir name in browser's user data: "Default", "Profile 1", ...
    val displayName: String,  // profile.info_cache[id].name (the user-visible label in Chrome)
)

// core/model/BrowserFamily.kt
enum class BrowserFamily {
    Chromium,   // Chrome, Edge, Brave, Vivaldi, Opera, Chromium
    Firefox,    // (out of MVP scope, but enum value reserved)
    Safari,     // (out of MVP scope, no profile detection planned)
    Other,      // anything we don't recognise — no profile expansion
}
```

`Browser` гетит два новых nullable-поля:

```kotlin
@Serializable
data class Browser(
    val bundleId: String,
    val displayName: String,
    val applicationPath: String,
    val version: String?,
    val profile: BrowserProfile? = null,    // NEW
    val family: BrowserFamily? = null,       // NEW
)
```

Default null'ы обеспечивают backward-compat: старая discovery / manual-add / persistence продолжат работать без правок (старые JSON-блобы прочитаются, недостающие поля заполнятся null).

### `BrowserId` derivation

```kotlin
fun Browser.toBrowserId(): BrowserId =
    if (profile != null) BrowserId("$applicationPath#${profile.id}")
    else BrowserId(applicationPath)
```

Когда профиль есть, ID включает суффикс `#<dir>`. Когда нет — старая форма (для всего, что без профилей: одиночный Chrome, Safari, manual-add записи).

### Per-bundleId Chromium directory map

```kotlin
// domain/ChromiumUserDataDirs.kt — single source of truth for which bundleIds
// are Chromium-family AND where their per-user data lives. Adding another
// browser = one entry here, no other changes.
internal data class ChromiumProfileSource(
    val userDataPath: String,  // relative to ~/Library/Application Support/
)

internal val chromiumUserDataPaths: Map<String, String> = mapOf(
    "com.google.Chrome"           to "Google/Chrome",
    "com.google.Chrome.beta"      to "Google/Chrome Beta",
    "com.google.Chrome.dev"       to "Google/Chrome Dev",
    "com.google.Chrome.canary"    to "Google/Chrome Canary",
    "com.microsoft.Edge"          to "Microsoft Edge",
    "com.microsoft.Edge.beta"     to "Microsoft Edge Beta",
    "com.microsoft.Edge.dev"      to "Microsoft Edge Dev",
    "com.brave.Browser"           to "BraveSoftware/Brave-Browser",
    "com.brave.Browser.beta"      to "BraveSoftware/Brave-Browser-Beta",
    "com.brave.Browser.nightly"   to "BraveSoftware/Brave-Browser-Nightly",
    "com.vivaldi.Vivaldi"         to "Vivaldi",
    "com.operasoftware.Opera"     to "com.operasoftware.Opera",
    "com.operasoftware.OperaGX"   to "com.operasoftware.OperaGX",
    "org.chromium.Chromium"       to "Chromium",
)

internal fun BrowserFamily.Companion.detect(bundleId: String): BrowserFamily =
    when {
        bundleId in chromiumUserDataPaths -> Chromium
        bundleId == "org.mozilla.firefox" -> Firefox
        bundleId.startsWith("com.apple.Safari") -> Safari
        else -> Other
    }
```

Single source of truth. Добавить ещё один Chromium-форк = одна строка в Map.

### `Local State` парсер

```
~/Library/Application Support/<userDataPath>/Local State
```

JSON-структура (упрощённо):
```json
{
  "profile": {
    "info_cache": {
      "Default":   { "name": "Vlad", "gaia_name": "vlad@gmail.com", ... },
      "Profile 1": { "name": "Work", "gaia_name": "vlad@work.com", ... }
    }
  }
}
```

Парсер `ChromiumProfileScanner.scan(userDataDir: Path): List<BrowserProfile>`:

- Файл `Local State` отсутствует → пустой список (браузер ни разу не запускался).
- JSON битый → пустой список.
- `profile.info_cache` отсутствует / не объект → пустой список.
- Иначе для каждого ключа выдать `BrowserProfile(id = key, displayName = info["name"] ?: info["gaia_name"] ?: key)`.

Результат сортируется по ключу так, чтобы `"Default"` шёл первым, остальные — лексикографически (`"Profile 1"`, `"Profile 2"`, ...). Это даёт стабильный порядок.

### Discovery flow

`MacOsBrowserDiscovery.discover()`:

1. Сегодня: находим `.app` → `InfoPlistReader` → `Browser`. Так и оставляем.
2. После сборки парентского `Browser`: `family = BrowserFamily.detect(bundleId)`. Сохраняем в поле.
3. Если `family == Chromium`:
   - Берём `userDataPath` из map.
   - Читаем профили через `ChromiumProfileScanner`.
   - Если профилей **≥ 2**, эмитим N записей: каждая с `family = Chromium`, `profile = ...`. Parent-record (без profile) **не эмитим** — он бесполезен, ссылка пошла бы в активный профиль, что неоднозначно.
   - Если **0 или 1** — эмитим одну запись без profile (как сейчас).
4. Если `family != Chromium` — эмитим одну запись, `profile = null`.

### Launcher

`MacOsLinkLauncher.openIn(browser, url)`:

- Если `browser.profile != null` AND `browser.family == BrowserFamily.Chromium`:
  ```
  open -na "<browser.applicationPath>" --args --profile-directory=<browser.profile.id> <url>
  ```
  Через тот же `processFactory` — теста ради единый кодпуть.
- Иначе (текущий поведение):
  ```
  open -a "<browser.applicationPath>" -- <url>
  ```

**Почему `-n`**: без `-n`, если Chromium-браузер уже запущен, macOS пропускает URL через Apple Event «Open URL» (этот event не несёт `--profile-directory=`), а флаг профиля идёт отдельным путём. В итоге активный профиль переключается в существующем окне, но URL «теряется» — пользователь видит, что профиль сменился, но никакой страницы не открылось. С `-n` `open` форсит новый процесс Chromium, тот видит и URL, и флаг вместе, обрабатывает их атомарно и через Chromium IPC хендофит работу в живой инстанс.

### UI

Везде, где сейчас рендерится `browser.displayName` — поменять на:
```kotlin
val label = if (browser.profile != null) {
    "${browser.displayName} — ${browser.profile.displayName}"
} else {
    browser.displayName
}
```

Места:
- `BrowserPickerScreen` — каждая строка picker'а.
- `BrowserRow` в Settings → Browsers section.
- `BrowserDropdown` в Settings → Rules section.

Версия `(${browser.version})` (там где она есть) — продолжаем показывать как было, рядом с label.

### Persistence migration (strict)

Старые ID в storage:
- `excludedBrowserIds` хранит `BrowserId(applicationPath)` без `#`.
- `browserOrder` — то же.
- `manualBrowsers` — `Browser` без `profile`/`family`. Эти продолжат работать как раньше: parser проигнорирует отсутствующие поля при де-сериализации (defaults).
- `rules` — `UrlRule.browserId` без `#`.

После релиза, для пользователя у которого Chrome имеет 2+ профилей:
- Discovery теперь выдаёт `BrowserId("/Applications/Google Chrome.app#Default")` и `#Profile 1` вместо `/Applications/Google Chrome.app`.
- Старые exclusions для `/Applications/Google Chrome.app` не сматчат ни одной из новых записей → exclusion «осиротеет».
- Order аналогично.
- Rules аналогично — `RuleEngine.findFirstApplicable` не найдёт browser с этим id, log `[ruleEngine] ... skipped: browser not installed`, сматчится следующее правило либо picker.

Strict trade-off: пользователь восстанавливает настройки руками. В CLAUDE.md добавится note. Формальной in-code migration не делаем (lenient вариант = усложнение всех мест с матчингом).

## Швы по решениям

| Решение | Если флипнуть → меняется |
|---|---|
| **#1 Только Chromium** | `BrowserFamily.detect(bundleId)` + `chromiumUserDataPaths`. Добавить Firefox = новый scanner + ветвь в discovery. Существующая логика не трогается. |
| **#2 Не дробим при 1 профиле** | `MacOsBrowserDiscovery` проверка `if (profiles.size >= 2)`. Поменять на `>= 1` — один файл. |
| **#3 Strict migration** | Никакого специального кода нет. Если хочется lenient — поменять `Browser.toBrowserId()` (например, добавить альтернативную форму без `#`) и/или `RuleEngine`/exclusions matcher. |
| **#4 Без аватарок** | UI label format. Добавить аватарки = + reading PNG из `Avatars/<info.avatar_index>.png` + Compose Image в `BrowserRow` / picker. Не трогает discovery / launcher. |
| **#5 Профили — first-class** | `BrowserId` derivation. Если откатывать на (b) — переключить `toBrowserId()` обратно на голый path и убрать profile из дискаверной экспансии. |

## Шаги реализации (порядок)

1. **Step 1** — `BrowserFamily` enum, `BrowserProfile` data class, расширение `Browser`. `Browser.toBrowserId()` обновлён, тесты на ID.
2. **Step 2** — `ChromiumUserDataDirs.kt`: map + `BrowserFamily.detect`. Тесты-таблица.
3. **Step 3** — `ChromiumProfileScanner`: readPlist-style scanner для `Local State`, тесты на JSON-edge-cases.
4. **Step 4** — `MacOsBrowserDiscovery`: интеграция scanner'а, эмиссия N записей. Тесты с фейковым ChromiumProfileScanner.
5. **Step 5** — `MacOsLinkLauncher`: профильная ветка с `--args --profile-directory=...`. Тесты с фейк processFactory.
6. **Step 6** — UI: `label` формат в `BrowserPickerScreen`, `BrowserRow`, `BrowserDropdown`.
7. **Step 7** — Documentation: CLAUDE.md status table + ai_stages/00_overview.md row + migration note.
8. **Step 8** — Сборка зелёная + ручной прогон + version bump (`1.0.1` → `1.0.2`).

После каждого шага — `./gradlew build` зелёный.

## Тест-план

### Автотесты

**`Browser.toBrowserId()`:**
- Без profile → `BrowserId(applicationPath)` (current behavior).
- С profile → `BrowserId("$applicationPath#${profile.id}")`.

**`BrowserFamily.detect`:**
- `com.google.Chrome` → Chromium.
- `org.mozilla.firefox` → Firefox.
- `com.apple.Safari` → Safari.
- `com.example.unknown` → Other.

**`ChromiumProfileScanner`:**
- Local State отсутствует → emptyList.
- Битый JSON → emptyList.
- info_cache отсутствует → emptyList.
- 1 профиль (Default) → 1 entry.
- 2+ профилей → entries в правильном порядке (Default первый).
- Имя берётся из `name`, если нет — `gaia_name`, если нет — id.
- Юникод/emoji в имени — не падает.

**`MacOsBrowserDiscovery`:**
- Chromium с 0 профилей (Local State отсутствует) → 1 запись без profile.
- Chromium с 1 профилем → 1 запись без profile (per Q2).
- Chromium с 2+ профилями → N записей, parent-запись отсутствует.
- Не-Chromium (Safari) → 1 запись без profile.

**`MacOsLinkLauncher`:**
- browser без profile → старая команда `open -a <path> -- <url>`.
- browser с profile + Chromium family → `open -a <path> --args --profile-directory=<id> <url>`.
- browser с profile но Other family (теоретически невозможно из дискавери, но защитная проверка) → fall back to plain `open -a`.

**`AppSettings` round-trip:** проверяем, что новые поля `Browser.profile` / `Browser.family` корректно сериализуются в `manualBrowsers`. (manual-add в MVP не создаёт профильные записи, но JSON-shape должен быть стабилен.)

### Ручные тест-кейсы

После пересборки `.app`:

#### TC-1. Browser с одним профилем не дробится

1. У тебя должен быть Chromium-браузер (например, Chromium.app), где никогда не создавались доп. профили.
2. Запусти Settings → Browsers. Этот браузер должен быть представлен **одной** записью «Chromium» (без суффикса).
3. `open https://example.com` → picker показывает его как «Chromium».

#### TC-2. Browser с 2+ профилями дробится

1. У тебя должен быть Chrome с двумя профилями (Default + Profile 1, например «Personal» + «Work»).
2. Settings → Browsers — две записи: «Google Chrome — Personal» и «Google Chrome — Work» (или как там названы).
3. `open https://example.com` → picker показывает оба варианта.
4. Кликни «Google Chrome — Work» → URL должен открыться именно в окне профиля Work (Chrome переключится / откроет вкладку в нужном профиле).

#### TC-3. Picker / Settings / Rules — профили first-class

1. В Settings → Browsers исключи «Google Chrome — Personal». Открой picker — должен быть только «Chrome — Work» (плюс другие браузеры, но не Personal).
2. В Settings → Rules → создай правило `*.work.com → Chrome — Work`.
3. `open https://stuff.work.com` → должен открыться напрямую в Work-профиле без picker'а.

#### TC-4. Refresh подхватывает изменения

1. В открытом приложении: переключи фокус на Chrome, добавь третий профиль вручную.
2. Вернись в Settings → жми refresh-кнопку → новый профиль появится в списке.

#### TC-5. Strict migration — старые exclusions осиротели

(если ты на момент ребилда уже что-то исключил/настроил для голого Chrome без профилей)

1. До ребилда: ты исключил «Chrome» в Settings → Browsers.
2. После ребилда: в picker'е появятся обе профильные записи Chrome (исключение не применилось, оно осиротело по path-only ID).
3. Это ожидаемо. Заново исключи нужный профиль.

#### TC-6. Невалидный Local State не ломает discovery

1. Если файл `Local State` испорчен, удалить или переименовать — discovery должен дать одну запись «Chrome» без падения.

## Implementation notes

(заполняется по ходу реализации)
