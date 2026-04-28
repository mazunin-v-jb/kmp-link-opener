# Стадия 4 — Persistence настроек

## Цель

Добавить слой хранения пользовательских настроек и UI для их редактирования. После этой стадии тема, язык, autostart и набор исключений из браузеров должны:

1. Сохраняться между запусками приложения.
2. Быть доступны через реактивный API (`StateFlow<AppSettings>`).
3. Иметь UI на экране Settings (с заглушкой секции exclusions, пока не подъедет стадия 2).
4. Покрываться unit-тестами.

Реальное переключение темы/языка приложения и реальный список браузеров — задача стадий 5 и 2 соответственно.

## Не входит в стадию 4

- Реальное применение темы/языка к UI приложения (стадия 5).
- Список реально установленных браузеров для секции exclusions — стадия 2.
- Кастомные правила открытия (стадия 6).
- AutoStart на Windows/Linux (стадии 7/8).

## Зависимости с другими стадиями

- **От стадии 2:** только тип `BrowserId`. Определяем здесь как `value class BrowserId(val value: String)`. Стадия 2 переиспользует этот тип. Если коллега уже определил иначе — короткий рефакторинг при интеграции.
- **С стадиями 3, 5, 6:** независимы.

## Архитектура изменений

### Модели (`shared/commonMain/core/model/`)

```kotlin
enum class AppTheme { System, Light, Dark }
enum class AppLanguage { System, En, Ru }

@JvmInline
@Serializable
value class BrowserId(val value: String)

@Serializable
data class AppSettings(
    val theme: AppTheme = AppTheme.System,
    val language: AppLanguage = AppLanguage.System,
    val autoStartEnabled: Boolean = false,
    val excludedBrowserIds: Set<BrowserId> = emptySet(),
)
```

### Domain (`shared/commonMain/domain/`)

```kotlin
// repository
interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    suspend fun updateTheme(theme: AppTheme)
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun setAutoStart(enabled: Boolean)
    suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean)
}

// usecase — каждый use case тонкая обёртка вокруг репозитория
class GetSettingsFlowUseCase(repo): operator fun invoke(): StateFlow<AppSettings>
class UpdateThemeUseCase(repo): suspend operator fun invoke(theme)
class UpdateLanguageUseCase(repo): suspend operator fun invoke(language)
class SetAutoStartUseCase(repo): suspend operator fun invoke(enabled)
class SetBrowserExcludedUseCase(repo): suspend operator fun invoke(id, excluded)
```

### Platform (`shared/commonMain/platform/`)

```kotlin
interface AutoStartManager {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun isEnabled(): Boolean
}
```

JVM implementations (`shared/jvmMain/platform/`):

- `MacOsAutoStartManager` — пишет/удаляет `~/Library/LaunchAgents/dev.hackathon.linkopener.plist`.
  - **Ограничение прототипа:** путь к исполняемому файлу из `LaunchAgent` указывает на `java -cp <classpath> dev.hackathon.linkopener.app.MainKt` если запущено из gradle, либо на бандл `.app/Contents/MacOS/...` если упаковано. В коде помечено `TODO`, чтобы при packaging выправить путь.
- `NoOpAutoStartManager` — для Windows/Linux. `setEnabled` ничего не делает, `isEnabled` возвращает false. Подменим в стадиях 7/8.
- `PlatformFactory.createAutoStartManager()` выбирает реализацию по `os.name`.

### Data (`shared/commonMain/data/`)

```kotlin
class SettingsRepositoryImpl(
    private val store: Settings,                    // multiplatform-settings
    private val json: Json,
    private val autoStartManager: AutoStartManager,
) : SettingsRepository {
    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    // load() читает все ключи и собирает AppSettings, fallback на defaults
    // updateTheme/Language/setAutoStart/setBrowserExcluded:
    //   1) для autoStart — позвать autoStartManager.setEnabled
    //   2) записать в store
    //   3) обновить _settings
}
```

Ключи в storage: `settings.theme`, `settings.language`, `settings.autoStart`, `settings.exclusions` (последний — JSON-строка `["safari","firefox"]`).

### UI (`shared/commonMain/ui/settings/`)

```kotlin
class SettingsViewModel(
    getSettings: GetSettingsFlowUseCase,
    private val updateTheme: UpdateThemeUseCase,
    private val updateLanguage: UpdateLanguageUseCase,
    private val setAutoStart: SetAutoStartUseCase,
    private val scope: CoroutineScope,
) {
    val settings: StateFlow<AppSettings> = getSettings()
    fun onThemeSelected(theme: AppTheme) { scope.launch { updateTheme(theme) } }
    fun onLanguageSelected(language: AppLanguage) { scope.launch { updateLanguage(language) } }
    fun onAutoStartChanged(enabled: Boolean) { scope.launch { setAutoStart(enabled) } }
}
```

`SettingsScreen` расширяется до:

- секция «Appearance» — dropdown для темы (System/Light/Dark)
- секция «Language» — dropdown для языка (System/English/Русский)
- секция «System» — toggle «Start at login» (binds to autoStart)
- секция «Browser exclusions» — заглушка с TODO (нет данных до стадии 2)

Используем `material3` компоненты: `ExposedDropdownMenuBox`, `Switch`, `Card`/`Column`.

Строки временно захардкожены на английском — i18n будет в стадии 5.

### DI (`desktopApp/.../AppContainer.kt`)

`AppContainer` расширяется:

```kotlin
class AppContainer {
    val coroutineScope = MainScope()
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsStore: Settings = JvmPreferencesSettings(
        Preferences.userRoot().node("dev/hackathon/linkopener")
    )
    private val autoStartManager = PlatformFactory.createAutoStartManager()
    private val settingsRepository = SettingsRepositoryImpl(settingsStore, json, autoStartManager)
    
    val getAppInfoUseCase = GetAppInfoUseCase(...)
    val getSettingsFlowUseCase = GetSettingsFlowUseCase(settingsRepository)
    val updateThemeUseCase = UpdateThemeUseCase(settingsRepository)
    val updateLanguageUseCase = UpdateLanguageUseCase(settingsRepository)
    val setAutoStartUseCase = SetAutoStartUseCase(settingsRepository)
    val setBrowserExcludedUseCase = SetBrowserExcludedUseCase(settingsRepository)
    
    fun newSettingsViewModel() = SettingsViewModel(
        getSettings = getSettingsFlowUseCase,
        updateTheme = updateThemeUseCase,
        updateLanguage = updateLanguageUseCase,
        setAutoStart = setAutoStartUseCase,
        scope = coroutineScope,
    )
}
```

`TrayHost` создаёт `SettingsViewModel` через `container.newSettingsViewModel()` и передаёт в `SettingsScreen`.

## Тесты

### `shared/commonTest`

- `SettingsRepositoryImplTest` — на `MapSettings` (in-memory), фейковый `AutoStartManager`. Покрывает: дефолты, обновление каждого поля, что обновляются и stored-значения и StateFlow, что setAutoStart вызывает manager.
- `SettingsViewModelTest` — фейковый `SettingsRepository`, проверяет что viewmodel дёргает правильные use cases и форвардит state.

### `shared/jvmTest`

- `MacOsAutoStartManagerTest` — использует `kotlin.io.path.createTempDirectory`, проверяет что `setEnabled(true)` создаёт plist с правильным `Label`, `setEnabled(false)` его удаляет, `isEnabled` возвращает корректное значение.

Все тесты используют `kotlinx.coroutines.test.runTest`.

## Acceptance criteria

- [ ] `./gradlew :shared:jvmTest` — все тесты проходят (старые + новые).
- [ ] `./gradlew build` — успешно.
- [ ] `./gradlew :desktopApp:run` — приложение запускается, открывается Settings, доступны три контрола (тема/язык/autostart) + заглушка exclusions.
- [ ] Изменения в Settings сохраняются между перезапусками (проверяется вручную или через тест repository).
- [ ] AutoStart на macOS реально пишет/удаляет plist (проверяется тестом + ручной верификацией `ls ~/Library/LaunchAgents/`).
- [ ] Exclusions UI помечен `TODO: integrate with stage 2 BrowserRepository`.
- [ ] Все упоминания placeholder-иконки и intentional-stubs помечены `TODO`.

## Что подсвечиваем при коммите

- Коммит на ветке `stage/04-settings-persistence`. **Не пушить.**
- В чате упомянуть TODO-метки: иконка трея (всё ещё placeholder) и exclusions UI (ждёт стадию 2).
- Ничего сенситивного (ключи/пароли) в стадии 4 не возникает; LaunchAgent plist не содержит секретов, только путь к исполняемому файлу.

## Ручные шаги

Для стадии 4 не требуются — всё работает из `gradle run`. AutoStart-toggle технически создаёт LaunchAgent, но он будет указывать на java-команду из dev-окружения; реально стартовать приложение при логине будет некорректно до момента, когда оно упаковано в `.app`. Это полирнётся при первом packaging-релизе.
