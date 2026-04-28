# Stage 06 — Custom URL → browser rules

## Цель

Закрыть `prompts/1_Prompt.md:26`:

> Настройка кастомных правил для открытия ссылок в определенных браузерах.

То есть: пользователь задаёт правила вида «открывай `*.youtube.com` в Firefox». Когда приходит URL, мы сначала проверяем, попадает ли он под какое-то правило; если да — открываем напрямую в указанном браузере, минуя picker. Если нет — обычный flow с picker.

## Зафиксированные дизайн-решения

Перед началом обсудили развилки. Текущий выбор:

1. **Синтаксис паттерна:** host-glob. `*.youtube.com`, `youtube.com`, `*.work.*`. `*` матчит любую последовательность символов **включая точки** — то есть `*.example.com` покрывает `foo.example.com` и `bar.baz.example.com`. Ничего сложнее.
2. **Приоритет:** first-match-wins на user-ordered списке. Стрелочки `↑↓` как у браузеров.
3. **Что матчим:** только `url.host`, не полный URL.
4. **Конфликт с exclusions:** **exclusion побеждает** — если правило указывает на исключённый браузер, правило тихо скипается, fall-through на picker.
5. **Браузер из правила исчез** (uninstall, удалили manual-запись, или путь сменился): silently skip + лог в `linkopener.debug=true`.
6. **Хранение:** extend `AppSettings.rules: List<UrlRule>`, методы в `SettingsRepository` — симметрично существующим полям (`manualBrowsers`, `excludedBrowserIds`, `browserOrder`).

Каждое из этих решений изолировано в одном файле, чтобы флип решения был локальным изменением. См. **Швы** ниже.

## В скоупе

- `UrlRule` модель + glob-матчинг + `RuleEngine` (resolve(url) → Direct/Picker).
- Persistence в `AppSettings.rules`.
- `SetRulesUseCase` (bulk) + VM-методы для add/remove/move/update.
- `PickerCoordinator` сначала спрашивает `RuleEngine`; если Direct — открывает напрямую, иначе обычный flow.
- Новая секция `Rules` в Settings sidebar; inline-редактирование, без модалок.
- i18n строк (en/ru).
- Автотесты на каждый слой.
- Ручной тест-план для проверки на macOS.

## Не в скоупе

- Regex-паттерны (можно добавить отдельной флагом-checkbox-на-правило позже без перелопачивания всего).
- Матчинг по полному URL (path/query). Только host для MVP.
- Тестировалка «дай URL, скажи какое правило сработает». Полезно, но не критично.
- Импорт/экспорт правил между машинами.
- Глобальный default браузер «когда никакое правило не сработало» — текущее поведение (picker) сохраняется.

## Архитектура

### Модель

```kotlin
// core/model/UrlRule.kt
@Serializable
data class UrlRule(
    val pattern: String,
    val browserId: BrowserId,
)
```

Без отдельного `id` — список сам по себе источник правды, операции ходят по индексу. UI даёт упорядоченный список, индексы стабильны в рамках одной композиции.

`AppSettings.rules: List<UrlRule>` — добавляется как пятое поле.

### Швы по решениям

Каждое из шести зафиксированных решений изолировано так, чтобы флип менял ровно один файл:

| Решение | Если захочется флипнуть → меняется |
|---|---|
| **#1 host-glob** | `domain/HostGlobMatcher.kt` — один метод `matches(pattern, host): Boolean`. Замена на regex / wildcard-on-full-URL = переписать тело метода. |
| **#2 first-match-wins** | `domain/RuleEngine.kt`, метод `findFirstMatch(...)`. Поменять алгоритм выбора = поменять одну функцию. |
| **#3 матчим только host** | `domain/RuleEngine.kt`, метод `extractTarget(url)`. Сегодня возвращает `URL(url).host`. Хочется матчить полный URL — поменять одну строку. |
| **#4 exclusion wins** | `domain/RuleEngine.kt`, в `resolve(...)` строка `if (browser.toBrowserId() in exclusions) skip`. Удалить — правило победит. |
| **#5 silently skip + debug log** | `domain/RuleEngine.kt`, в `resolve(...)` блок `if (browser == null)`. Заменить на сообщение в notice-flow / падение / что угодно. |
| **#6 extend AppSettings vs RulesRepository** | `data/SettingsRepositoryImpl.kt` + `AppSettings.kt`. Если выносим в `RulesRepository`, переделывается persistence-слой, но domain (`UrlRule`, `HostGlobMatcher`, `RuleEngine`) не трогается. |

### Слои

```
core/model/
├── UrlRule.kt                        # @Serializable data class

domain/
├── HostGlobMatcher.kt                # Решение #1: только host-glob.
└── RuleEngine.kt                     # Решения #2-#5: first-match, host-extract, exclusion-wins, missing-skip.

domain/usecase/
└── SetRulesUseCase.kt                # Bulk replace списка правил.

data/SettingsRepositoryImpl.kt        # Решение #6: KEY_RULES + encode/decode.

ui/picker/PickerCoordinator.kt        # Спрашивает RuleEngine перед picker'ом.
ui/settings/RulesSection.kt           # UI секция (новая).
ui/settings/SettingsViewModel.kt      # VM-методы поверх SetRulesUseCase.
ui/settings/SettingsScreen.kt         # NavSection.Rules + проводка.
```

### `HostGlobMatcher` (решение #1)

Чистая функция (object). Без зависимостей.

```kotlin
object HostGlobMatcher {
    fun matches(pattern: String, host: String): Boolean { ... }
}
```

Семантика:
- `*` матчит любую последовательность символов, **включая точки**. То есть `*.example.com` покрывает и `foo.example.com`, и `bar.baz.example.com`.
- **Leading `*.`** — особенный: трактуется как «опциональный субдомен-префикс». Поэтому `*.vk.com` матчит и голый `vk.com`, и `m.vk.com`, и `oauth.m.vk.com`. Это cookie-domain-семантика, под которую большинство пользователей пишут такие паттерны.
- Match case-insensitive (host'ы регистр-независимы по стандарту).
- Pattern и host trim-ятся (whitespace-only паттерны не валидны — отдельная валидация в use case).
- Реализация — простая конвертация паттерна в regex: leading `*.` → `(.*\.)?`, прочие `*` → `.*`, остальные символы escape-аются.

Альтернатива (если флипнем на полный URL): метод принимает `target: String`, в нём `*` матчит любые символы. Будет либо новый матчер, либо параметризация.

### `RuleEngine` (решения #2-#5)

```kotlin
sealed interface RuleDecision {
    data class Direct(val browser: Browser) : RuleDecision
    data object Picker : RuleDecision
}

class RuleEngine(
    private val matcher: HostGlobMatcher = HostGlobMatcher,
    private val debug: Boolean = false,
    private val log: (String) -> Unit = ::println,
) {
    fun resolve(
        url: String,
        rules: List<UrlRule>,
        browsers: List<Browser>,
        exclusions: Set<BrowserId>,
    ): RuleDecision {
        val host = extractTarget(url) ?: return RuleDecision.Picker
        for (rule in rules) {
            if (!matcher.matches(rule.pattern, host)) continue
            val browser = browsers.firstOrNull { it.toBrowserId() == rule.browserId }
            if (browser == null) {
                if (debug) log("[ruleEngine] rule '${rule.pattern}' → ${rule.browserId.value} skipped: browser not installed")
                continue
            }
            if (browser.toBrowserId() in exclusions) {
                if (debug) log("[ruleEngine] rule '${rule.pattern}' → ${browser.displayName} skipped: browser is excluded")
                continue
            }
            return RuleDecision.Direct(browser)
        }
        return RuleDecision.Picker
    }

    private fun extractTarget(url: String): String? =
        runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
}
```

Заметки:
- `log` — инжектится для тестируемости (тесты собирают строки в список).
- `debug` — в проде идёт из `linkopener.debug=true` (через AppContainer).
- `extractTarget` живёт в одном месте; полный-URL-матчинг = заменить тело и параметр matcher'а.

### Persistence

`SettingsRepositoryImpl`:
- `KEY_RULES = "settings.rules"`
- Encode/decode через `kotlinx.serialization` `ListSerializer(UrlRule.serializer())`.
- Битый JSON → `emptyList()`, как для других полей.

`SettingsRepository` интерфейс:
```kotlin
suspend fun setRules(rules: List<UrlRule>)
```

(Bulk — VM считает новый список и пушит. Меньше методов в интерфейсе, симметрично `setBrowserOrder`.)

### `PickerCoordinator`

```kotlin
fun handleIncomingUrl(url: String) {
    scope.launch {
        try {
            val all = discoverBrowsers()
            val settings = getSettings().value
            val available = all.filterNot { it.toBrowserId() in settings.excludedBrowserIds }
            val ordered = applyUserOrder(available, settings.browserOrder)
            // NEW: ask the rule engine first. Note `all` (not `available`) —
            // the engine handles its own exclusion check, so it sees the full
            // list and can log "skipped: excluded" cleanly.
            when (val decision = ruleEngine.resolve(url, settings.rules, all, settings.excludedBrowserIds)) {
                is RuleDecision.Direct -> launcher.openIn(decision.browser, url)
                RuleDecision.Picker -> _state.value = PickerState.Showing(url = url, browsers = ordered)
            }
        } catch ...
    }
}
```

### VM

```kotlin
fun onAddRule(pattern: String, browserId: BrowserId) { /* new = current + UrlRule(pattern, browserId), call setRules */ }
fun onRemoveRule(index: Int) { ... }
fun onMoveRule(fromIndex: Int, toIndex: Int) { ... }
fun onUpdateRulePattern(index: Int, pattern: String) { ... }
fun onUpdateRuleBrowser(index: Int, browserId: BrowserId) { ... }
```

Каждый метод вычисляет новый список и зовёт `setRules(newList)`. Все ошибки/невалидные индексы — silently no-op (как для existing `reorder`).

### UI

Новый `NavSection.Rules` enum value. В sidebar — новый `NavItem` под существующими.

`RulesSection` композит: вертикальный список правил + кнопка `+ Add rule…` снизу.

Каждая строка:
```
[pattern TextField]   →   [browser dropdown]   [↑] [↓] [×]
```

- TextField: pattern, on-change → `onUpdateRulePattern(i, ...)`.
- Dropdown: список всех browsers (discovered + manual, из `vm.browsers`), on-select → `onUpdateRuleBrowser(i, id)`.
- ↑↓: `onMoveRule(i, i±1)`, disabled на крайних.
- ×: `onRemoveRule(i)`.

`+ Add rule…` создаёт пустую строку с pattern = `""` и browserId = первый из имеющихся (или null-ish placeholder, но нагляднее заполнить дефолтом).

i18n строки (EN/RU):
- `section_rules` / Правила
- `add_rule` / + Добавить правило…
- `rule_pattern_placeholder` / Pattern (e.g. `*.youtube.com`)
- `rule_no_browsers` / No browsers available — discover or add some first
- `rule_remove_content_description` / Удалить правило
- + переиспользуем существующие `move_up` / `move_down`.

## Шаги реализации (порядок)

1. **Step 1 — `UrlRule` + `HostGlobMatcher`.** Изолированно, чисто, легко тестируется.
2. **Step 2 — `RuleEngine`.** Тоже изолированно, без UI. Все решения #2-#5 живут тут.
3. **Step 3 — `AppSettings.rules` + persistence.** Solo тестируется по round-trip.
4. **Step 4 — `SetRulesUseCase` + DI.** Связываем тривиальные слои.
5. **Step 5 — `PickerCoordinator` + tests.** Интегрируем engine в picker-flow.
6. **Step 6 — VM-методы.** Add/remove/move/update.
7. **Step 7 — UI секция.** Новая `RulesSection`, NavSection.Rules.
8. **Step 8 — Сборка + ручной прогон + документация.** Update `CLAUDE.md` table, `00_overview.md`.

После каждого шага — сборка зелёная.

## Тест-план

### Автотесты

**HostGlobMatcherTest:**
- `*.example.com` matches `foo.example.com` И `bar.baz.example.com` (т.к. `*` покрывает точки).
- Точное совпадение: `youtube.com` matches `youtube.com`, не matches `www.youtube.com`.
- `*.work.*`: matches `email.work.com`, `git.work.io`, и `email.sub.work.io.test`. Не matches `work.com`.
- Pattern с `**`: эквивалентен `*` — тестируем что не падает.
- Case-insensitive: `*.YouTube.com` matches `Foo.youtube.com`.
- Empty pattern, empty host: false.
- Pattern с regex-метасимволами (`. + ? ( ) { } | ^ $ \`) — экранируются, считаются буквальными. `1.0.0` matches `1.0.0`, не matches `1x0x0`.

**RuleEngineTest:**
- First-match-wins: два правила, оба матчат — побеждает первое.
- Match → Direct(browser).
- No match → Picker.
- Match → browser отсутствует в списке → продолжает поиск, в итоге Picker; debug-log зафиксирован.
- Match → browser в exclusions → продолжает поиск, в итоге Picker; debug-log зафиксирован.
- Match → browser ok → Direct.
- URL без host (`mailto:foo@bar`) → Picker (extractTarget вернёт null).
- Невалидный URL → Picker.

**SettingsRepositoryImplTest:**
- `setRules` пишет ключ + `_settings` обновляется.
- Round-trip: записать `setRules(...)` → новый репо читает то же.
- Битый JSON в ключе → `emptyList()` fallback.
- Idempotent: повторный `setRules` с тем же значением — no-op.

**SetRulesUseCaseTest:** простая делегация, базовый smoke.

**PickerCoordinatorTest** (расширение существующих):
- Rule matches → `launcher.openIn(matchedBrowser, url)` вызван, picker НЕ показан.
- Rule не matches → picker показан как раньше.
- Rule matches, но browser excluded → picker показан (engine скипнул).

**SettingsViewModelTest:**
- `onAddRule(pattern, browserId)` → `settings.rules.last() == UrlRule(...)`.
- `onRemoveRule(index)` → элемент удалён.
- `onMoveRule(0, 1)` → swap.
- `onUpdateRulePattern(index, "new")` → элемент обновлён.
- `onUpdateRuleBrowser(index, newId)` → элемент обновлён.

### Ручные тест-кейсы

Перед прогоном: пересобрать `.app` и переустановить.

#### TC-1. Базовое срабатывание правила

1. Settings → Rules → `+ Add rule…` → pattern = `*.example.com`, browser = Firefox (или любой не-default).
2. Сохрани — закрой Settings, открой снова. Правило на месте.
3. `open https://example.com/test`. Picker НЕ должен показаться, страница открывается сразу в Firefox.
4. `open https://www.example.com/test`. То же.
5. `open https://other.com/test`. Picker должен появиться (не матчит правило).

#### TC-2. First-match-wins

1. Добавь два правила:
   - `*.example.com` → Chrome (на 1-й позиции)
   - `*.example.com` → Firefox (на 2-й)
2. `open https://foo.example.com`. Должен открыться Chrome.
3. Стрелочкой `↑` подними Firefox на 1-ю позицию.
4. Снова `open https://foo.example.com`. Должен открыться Firefox.

#### TC-3. Exclusion побеждает правило

1. Добавь правило `youtube.com` → Firefox.
2. В Settings → Browsers исключи Firefox.
3. `open https://youtube.com`. Picker должен появиться (Firefox в exclusions, правило скипнулось).
4. (Запускай с `-Dlinkopener.debug=true` чтобы увидеть в логе `[ruleEngine] … skipped: browser is excluded`.)
5. Верни Firefox в Included → правило снова срабатывает напрямую.

#### TC-4. Браузер из правила исчез

1. Добавь manual-браузер `/Apps/Foo.app` (через `+ Add browser…`).
2. Создай правило `*.foo.com` → этот manual-браузер.
3. Удали manual-браузер `×`.
4. `open https://www.foo.com`. Picker должен показаться (правило скипнулось, browser-not-found, debug-log записан).

#### TC-5. Невалидные паттерны не падают

1. Создай правило с pattern = `` (пустая строка). Сохрани (или сразу останется пустым).
2. `open https://anything.com`. Никакого падения. Правило просто не матчит ничего.
3. Pattern с пробелами `   `. То же.

#### TC-6. Persistence

1. Создай 3 правила в любом порядке.
2. Quit из трея.
3. Запусти заново. Все 3 правила в том же порядке.

#### TC-7. Невалидный URL / mailto

1. Через debug-меню (`-Dlinkopener.debug=true`) или вручную: попадает URL `mailto:foo@bar.com`.
2. Никаких правил по почте нет → ведёт себя как обычно (picker, как с http URL'ами с пустым host).

## Implementation notes

(заполняется по ходу реализации, если что-то отклоняется от плана)
