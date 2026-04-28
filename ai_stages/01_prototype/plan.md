# Стадия 1 — Минимальный работающий прототип

## Цель

Получить запускаемое Compose Desktop приложение, которое:
1. При старте сидит в системном трее, окно не открывается.
2. По клику на иконку трея показывает меню с пунктами **Settings** и **Quit**.
3. **Quit** — корректно завершает приложение.
4. **Settings** — открывает пустое окно с заголовком «Settings».
5. Содержит скелет архитектуры (модули, слои, DI), на который дальше можно навешивать фичи.
6. Покрыт базовыми тестами, которые проходят.

После этой стадии другие агенты смогут параллельно работать над browser discovery, settings persistence, темами и i18n.

## Не входит в стадию 1

- Реальный поиск браузеров, открытие ссылок.
- Регистрация приложения как браузера в системе.
- Настоящие настройки (тема, язык, autostart).
- Финальная иконка (используется заглушка с TODO).
- Поддержка Windows/Linux (хотя архитектура к ней готова — runtime-выбор реализаций по `os.name`).

## Изменения в репозитории

### Gradle

1. **`settings.gradle.kts`** — добавить `include(":desktopApp")`.
2. **`gradle/libs.versions.toml`** — добавить:
   - `compose-material3` (`org.jetbrains.compose.material3:material3`, version ref `compose-multiplatform`)
   - алиас `compose-desktop` (плагин уже есть как `compose-multiplatform`, но для модуля app нужен сам плагин — переиспользуем существующий).
3. **`shared/build.gradle.kts`** — добавить material3 в `commonMain` зависимости. Maven-publish конфигурация остаётся (это всё ещё библиотека).
4. **`desktopApp/build.gradle.kts`** — новый файл:
   - плагины: `kotlin.multiplatform`, `compose.multiplatform`, `compose.compiler`
   - target: `jvm("desktop")` с JVM 17
   - зависимость на `:shared`
   - `compose.desktop.application { mainClass = "my.company.linkopener.app.MainKt" }`
   - native distributions: пока без подписи/нотаризации, просто dmg/msi/deb для проверки сборки

### Структура `:shared` (создаём папки и заглушки)

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/
├── core/
│   └── model/
│       └── AppInfo.kt              # data class AppInfo(val name: String, val version: String)
├── domain/
│   ├── repository/
│   │   └── AppInfoRepository.kt    # interface
│   └── usecase/
│       └── GetAppInfoUseCase.kt    # тривиальный use case для smoke-теста архитектуры
├── data/
│   └── AppInfoRepositoryImpl.kt    # возвращает захардкоженные значения
└── ui/
    ├── settings/
    │   └── SettingsScreen.kt       # пустой Composable с заголовком
    └── tray/
        └── TrayMenuItems.kt        # data class описания пунктов меню (sealed)
```

`Fibonacci.kt` остаётся (не трогаем — отдельный пример из шаблона). Можно удалить позже.

```
shared/src/commonTest/kotlin/dev/hackathon/linkopener/
└── domain/usecase/
    └── GetAppInfoUseCaseTest.kt    # smoke-тест use case
```

### Структура `:desktopApp`

```
desktopApp/
├── build.gradle.kts
└── src/jvmMain/
    ├── kotlin/dev/hackathon/linkopener/app/
    │   ├── Main.kt                 # main() + application { TrayHost() }
    │   ├── AppContainer.kt         # composition root: создаёт repository → use case
    │   └── tray/
    │       └── TrayHost.kt         # Compose Tray + меню + управление окном настроек
    └── (иконки трея — заглушка через программный Painter,
       без файлов; см. PlaceholderTrayIcon, помечено TODO)
```

**Заметка по иконке:** в прототипе иконка трея — это `Painter`, рисующий примитивы (кружок на тёмном фоне) прямо в коде. Это убирает необходимость в бинарном PNG в репо и явно помечается TODO. Когда появится финальный дизайн, заменим на `painterResource("icons/app_tray_icon.png")` + добавим PNG в `resources/`.

## Контракты (минимально)

```kotlin
// commonMain
package dev.hackathon.linkopener.core.model
data class AppInfo(val name: String, val version: String)

package dev.hackathon.linkopener.domain.repository
interface AppInfoRepository { fun getAppInfo(): AppInfo }

package dev.hackathon.linkopener.domain.usecase
class GetAppInfoUseCase(private val repo: AppInfoRepository) {
    operator fun invoke(): AppInfo = repo.getAppInfo()
}

// data
package dev.hackathon.linkopener.data
class AppInfoRepositoryImpl : AppInfoRepository {
    override fun getAppInfo() = AppInfo(name = "Link Opener", version = "0.1.0")
}
```

`GetAppInfoUseCase` нужен только чтобы:
- продемонстрировать слоёную архитектуру (model → repository → use case → UI),
- дать тестам что-то осмысленное проверить,
- в дальнейшем на него можно опереться (например, показать версию в окне настроек).

## DI / composition root

`AppContainer` — обычный класс, собранный руками:

```kotlin
class AppContainer {
    private val appInfoRepository: AppInfoRepository = AppInfoRepositoryImpl()
    val getAppInfoUseCase = GetAppInfoUseCase(appInfoRepository)
}
```

В `Main.kt` создаётся один `AppContainer` и пробрасывается в `TrayHost`.

## Тесты

`GetAppInfoUseCaseTest` — проверяет, что use case возвращает `AppInfo` с непустым именем и версией. Простой smoke-тест: подтверждает, что DI собирается, классы видны из commonTest, gradle-конфигурация рабочая.

В будущих стадиях каждый use case получит полноценные тесты с моками репозиториев.

## Поведение на платформах (в рамках прототипа)

- На **macOS** окно приложения не должно появляться при старте — только tray. Compose Desktop `application { ... }` без `Window { ... }` создаёт «безоконное» приложение, всё что показывается — это `Tray`. ОК.
- Trayменю показывается на правый клик на всех платформах, на левый — поведение разное:
  - macOS: открывает popup-меню по умолчанию (если `Tray` так настроен).
  - Windows/Linux: обычно правый клик; левый — может вообще ничего не делать.
  - **Решение для прототипа:** используем стандартное поведение Compose `Tray` (popup на клик). Если потребуется кастомное поведение по левому клику — это решается на стадии 7/8 для соответствующих платформ.
- Иконка трея: монохромный квадратик-заглушка из ресурсов. `TODO: заменить иконку` рядом с её загрузкой.

## Acceptance criteria

- [ ] `./gradlew :shared:build` — успешно собирается.
- [ ] `./gradlew :shared:test` — все тесты проходят.
- [ ] `./gradlew :desktopApp:run` — приложение запускается на macOS, появляется иконка в трее, окно не открывается.
- [ ] Клик на иконку показывает меню с **Settings** и **Quit**.
- [ ] **Quit** завершает процесс.
- [ ] **Settings** открывает пустое окно с заголовком «Settings» (закрытие окна не должно завершать приложение — оно продолжает работать в трее).
- [ ] Иконка в трее — заглушка с `TODO`-комментарием в коде.
- [ ] Структура папок соответствует описанной выше.

## Ручные шаги после стадии 1

Для прототипа их не нужно — приложение запускается из IDE / `gradle run` без дополнительной настройки. Регистрация в системе как браузера будет на стадии 3.
