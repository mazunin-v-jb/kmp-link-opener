# Stage 047 — Browser profiles display toggle

## Цель

Глобальный чекбокс «показывать профили браузеров» в Settings → System. Когда включён (default) — поведение из стадии 4.6: Chrome с 2+ профилями разворачивается в N записей. Когда выключен — профильные записи свернуты в одну запись на parent-браузер (как до стадии 4.6).

## Зафиксированные дизайн-решения

1. **Глобальный флаг**, не per-browser. Per-browser — позже отдельной задачей если попросят.
2. **Default = `true`** — поведение по умолчанию совпадает с тем, что юзер видел до этого изменения.
3. **Strict, не lenient.** Когда чекбокс выключают, exclusions/order/rules с `#profileDir`-суффиксом перестают матчиться (orphan'ятся), как и в стадии 4.6 при первом появлении профилей. Симметрично включают чекбокс назад → старые без-`#`-ID орфан'ятся. Документируем в подписи к чекбоксу.
4. **Один decision-point — `BrowserRepositoryImpl`.** Discovery как и было выдаёт N записей с профилями; решение «свернуть/не свернуть» принимается на репо-слое после `mergeWithManual`.

## В скоупе

- `AppSettings.showBrowserProfiles: Boolean = true`.
- Persistence: новый ключ + JSON round-trip.
- `BrowserRepositoryImpl.collapseProfilesIfDisabled(list)` — сворачивает профильные записи в одну при `showBrowserProfiles == false`.
- `SetShowBrowserProfilesUseCase` (bulk-style, но тут просто bool).
- `SettingsViewModel.onShowBrowserProfilesChanged(Boolean)` + `loadBrowsers(false)` reload.
- UI: новая `Switch`-строка в Settings → System с подписью-предупреждением про orphaning.
- i18n строки (en/ru).
- Автотесты на каждый слой.
- Manual TC.

## Не в скоупе

- Per-browser toggle.
- Lenient ID-matching (когда `Chrome#Profile 1` в правилах сматчит свернутый «Chrome» record).
- Tooltip с расширенными подробностями (только однострочный description под switch).

## Архитектура

### Модель

```kotlin
@Serializable
data class AppSettings(
    ...
    val showBrowserProfiles: Boolean = true,
)
```

Default `true`: для пользователей, которые до апдейта уже работали с профилями, поведение не меняется.

### Persistence

`SettingsRepositoryImpl`:
- `KEY_SHOW_PROFILES = "settings.showBrowserProfiles"`.
- Хранение через `store.putBoolean / getBoolean(default = true)`.

`SettingsRepository`:
```kotlin
suspend fun setShowBrowserProfiles(enabled: Boolean)
```

### `BrowserRepositoryImpl` collapse

После `mergeWithManual(discovered)` — если флаг выключен, схлопываем профильные записи:

```kotlin
private fun collapseProfilesIfDisabled(list: List<Browser>): List<Browser> {
    if (settings.settings.value.showBrowserProfiles) return list
    return list.groupBy { it.applicationPath }.map { (_, group) ->
        if (group.size == 1) group.first()
        else group.first().copy(profile = null) // взять первый, обнулить профиль
    }
}
```

Применяется и в `getInstalledBrowsers()`, и в `refresh()`.

### VM

```kotlin
fun onShowBrowserProfilesChanged(enabled: Boolean) {
    scope.launch {
        setShowBrowserProfiles(enabled)
        loadBrowsers(forceRefresh = false) // re-merge с новым правилом
    }
}
```

### UI

В Settings → System (там, где сейчас Switch для autostart) добавляется ещё одна Switch-строка:

- Title: «Show browser profiles»
- Description: «When off, multi-profile browsers like Chrome show as one row. Note: rules / exclusions referencing specific profiles will not match while disabled.»

i18n keys:
- `show_browser_profiles_title` / Показывать профили браузеров
- `show_browser_profiles_description` / Когда выключено, ...

## Швы по решениям

| Решение | Если флипнуть → меняется |
|---|---|
| **Default = true** | Default value у `AppSettings.showBrowserProfiles`. |
| **Strict orphaning** | `BrowserRepositoryImpl.collapseProfilesIfDisabled`. Чтобы добавить lenient — менять matchers в `RuleEngine` / `PickerCoordinator` / exclusion-checks. |
| **Глобальный, не per-browser** | Сейчас `Boolean`. Чтобы сделать per-browser — `Set<String>` (по applicationPath или bundleId), новый method, новый UI. Discovery / launcher не трогаются. |

## Шаги реализации

1. **Step 1** — `AppSettings` + `SettingsRepository.setShowBrowserProfiles` + persistence. Round-trip тесты + idempotency. Обновить test fakes (4 штуки) under interface.
2. **Step 2** — `BrowserRepositoryImpl.collapseProfilesIfDisabled` + тесты на collapse-on/off.
3. **Step 3** — `SetShowBrowserProfilesUseCase` + VM `onShowBrowserProfilesChanged` + UI Switch + i18n.
4. **Step 4** — `./gradlew build` + manual TC + version bump 1.0.2 → 1.0.3.

## Тест-план

### Автотесты

- `SettingsRepositoryImplTest`: round-trip `showBrowserProfiles`, idempotent.
- `BrowserRepositoryImplTest`: при `showBrowserProfiles=false` Chrome с 2 профилями сворачивается в одну запись (без profile, family сохранён). При `true` — выдаёт 2 записи как и было.
- `SettingsViewModelTest`: `onShowBrowserProfilesChanged(false)` → settings.value меняется + browsers reload (профильные записи исчезли).

### Ручные тест-кейсы

#### TC-1. Дефолтное поведение

После update: чекбокс ON, в Settings → Browsers обе записи Chrome (Personal, Work). picker — то же.

#### TC-2. Выключение чекбокса

В Settings → System выключи «Show browser profiles». В Settings → Browsers — одна запись Chrome (без суффикса). picker — одна запись.

#### TC-3. Включение обратно

Включи чекбокс назад. Профильные записи возвращаются.

#### TC-4. Orphaning при выключении

1. Включи чекбокс, создай правило `*.work.com → Chrome — Work`.
2. Выключи чекбокс. `open https://stuff.work.com` → picker (правило указывает на ID `Chrome.app#Profile 1`, в текущем list'е такого нет → orphan, fall through).
3. Включи обратно. `open https://stuff.work.com` → правило снова работает напрямую в Work-профиле.

## Implementation notes

(заполняется по ходу)
