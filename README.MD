# Link Opener

A cross-platform link-opener desktop app: registers itself with the OS as a browser, intercepts URL clicks from any application, and lets you pick which browser (or mail client, or other URL handler) should handle the link. Built with Kotlin Multiplatform and Compose Multiplatform; targets macOS, Windows, and Linux.

> **Status:** early prototype. The tray app boots and shows a stub menu (Settings / Quit). Browser discovery, URL handling, and OS-level default-browser registration are upcoming stages.

## Modules

- **`:shared`** — Kotlin Multiplatform library containing all reusable logic: domain models, use cases, repositories, platform interfaces, and Compose UI components. Currently targets the JVM (one source set for all three desktop OSes).
- **`:desktopApp`** — runnable Compose Desktop application. Hosts `main()`, the system tray, and the composition root that wires the dependency graph.

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/
  core/      — domain models
  domain/    — repository interfaces, use cases
  data/      — repository implementations
  ui/        — Compose screens (tray menu, settings, browser picker)

shared/src/jvmMain/kotlin/dev/hackathon/linkopener/
  platform/  — OS-specific implementations (macOS / Windows / Linux),
               selected at runtime via os.name detection

desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/
  Main.kt           — entry point
  AppContainer.kt   — manual DI / composition root
  tray/             — tray icon + menu
```

See [`ai_stages/00_overview.md`](ai_stages/00_overview.md) for the full architecture write-up and stage-by-stage roadmap.

## Requirements

- JDK **17** or higher
- macOS, Windows, or Linux (current prototype is verified on macOS)
- Gradle is bundled via the wrapper — no local install needed

## Build & run

```bash
# Run the desktop app
./gradlew :desktopApp:run

# Build everything (compile + tests + assemble)
./gradlew build

# Run shared-module tests
./gradlew :shared:jvmTest

# Run a single test class
./gradlew :shared:jvmTest --tests "dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCaseTest"

# Produce native distributables (DMG / MSI / DEB) for the current OS
./gradlew :desktopApp:packageDistributionForCurrentOS
```

When the app is running, look for the tray icon in your menu bar / system tray. Click it for the **Settings** / **Quit** menu.

## Tech stack

- **Kotlin** 2.3.20 (Multiplatform)
- **Compose Multiplatform** 1.10.3 — Material 3 UI
- **kotlinx.coroutines** 1.10.2
- **kotlinx.serialization** 1.10.0 — for persisted settings and rules (JSON)
- **multiplatform-settings** 1.3.0 — key/value settings storage

Dependency versions are managed centrally in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Architecture

A simple layered architecture with no DI framework:

```
UI (Compose)
    │
    ▼
Domain (use cases) ──► Repository interfaces
                              │
                              ▼
                      Data (impls) ◄── Platform interfaces ◄── jvmMain (macOS / Windows / Linux impls)
```

- Use cases are plain classes with `operator fun invoke(...)`.
- Repositories are interfaces in `commonMain`; implementations live in `commonMain` (storage-backed) or `jvmMain` (OS-specific).
- The composition root (`AppContainer` in `:desktopApp`) wires everything by hand — no Hilt/Koin/etc.
- Platform-specific behavior is selected at runtime by inspecting `System.getProperty("os.name")`.

## Roadmap

The project is built incrementally so multiple agents can work in parallel after the prototype stabilizes the interfaces. See [`ai_stages/`](ai_stages/) for detailed plans.

1. **Prototype** — tray + stub settings window + scaffolding _(current)_
2. Browser discovery (macOS) via Launch Services
3. Link interception + browser-picker popup (macOS)
4. Persistent settings (theme, language, autostart, exclusions)
5. Themes & i18n (system / light / dark, ru / en)
6. Custom URL→browser rules
7. Windows support (registry, default-app registration)
8. Linux support (`.desktop` files, `xdg-mime`)

## Notes

- The current tray icon is a programmatically drawn placeholder (`PlaceholderTrayIcon`). It is marked with a `TODO` and will be replaced with a designed asset.
- Maven Central publishing has been removed — this repository is now an application, not a library.
