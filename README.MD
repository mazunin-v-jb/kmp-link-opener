# Link Opener

A cross-platform link-opener desktop app: registers itself with the OS as a browser, intercepts URL clicks from any application, and lets you pick which browser should handle the link. Built with Kotlin Multiplatform and Compose Multiplatform; targets macOS, Windows, and Linux.

> **Status:** macOS path is fully wired end-to-end. Register the app as the default browser → URLs route to us → a picker popup appears at the cursor → clicking a row launches the chosen browser via `open -a`. Settings (theme, language, autostart, browser exclusions) persist; the Settings UI watches the system default-browser binding live and shows a banner when we are not the default. Windows and Linux compile and run as tray-only stubs — discovery and link-launching are no-ops there until stages 7/8.

See [`CLAUDE.md`](CLAUDE.md) for the canonical stage-by-stage status table, [`ai_stages/00_overview.md`](ai_stages/00_overview.md) for the original master plan, and [`TECHDEBT.md`](TECHDEBT.md) for the running tech-debt list.

## Modules

- **`:shared`** — Kotlin Multiplatform library containing all reusable logic: domain models, use cases, repositories, platform interfaces, and Compose UI components. JVM target only — all three host OSes share the same source set, with platform impls picked at runtime.
- **`:desktopApp`** — runnable Compose Desktop application. Hosts `main()`, the system tray, the picker window, and the manual composition root that wires the dependency graph.

```
shared/src/commonMain/kotlin/dev/hackathon/linkopener/
  core/model/        — Browser, AppSettings, AppTheme, AppLanguage, BrowserId, AppInfo
  domain/repository/ — interfaces (BrowserRepository, SettingsRepository, AppInfoRepository)
  domain/usecase/    — Discover/Update/Set/Observe… (operator fun invoke)
  data/              — *RepositoryImpl backed by multiplatform-settings + JSON
  platform/          — interfaces (BrowserDiscovery, UrlReceiver, LinkLauncher,
                       DefaultBrowserService, AutoStartManager) + EmptyBrowserDiscovery
  ui/picker/         — PickerCoordinator, PickerState, BrowserPickerScreen
  ui/settings/       — SettingsScreen, SettingsViewModel, BrowsersState
  ui/strings/        — LocalizedLabels, LocalAppLocale (CompositionLocal nonce)
  ui/theme/          — LinkOpenerTheme, LinkOpenerColors, LinkOpenerTypography, ThemeResolution
  ui/icons/          — AppIcons (Material Symbols ImageVectors)
  ui/tray/           — TrayMenuAction

shared/src/commonMain/composeResources/values{,-ru}/strings.xml — i18n strings + arrays

shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/
  PlatformFactory.kt                 — runtime os-detection
  JvmUrlReceiver.kt                  — wraps Desktop.setOpenURIHandler
  PrintingLinkLauncher.kt            — fallback for non-mac hosts
  NoOpAutoStartManager.kt            — fallback for non-mac hosts
  macos/                             — BrowserDiscovery, AutoStartManager, DefaultBrowserService,
                                       LinkLauncher, AppBundleScanner, InfoPlistReader,
                                       PlistJsonParser, PlutilRunner
  windows/WindowsDefaultBrowserService.kt
  linux/LinuxDefaultBrowserService.kt

desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/
  Main.kt              — application { ... } entry point
  AppContainer.kt      — manual DI / composition root
  tray/TrayHost.kt     — Tray + Settings window
  tray/PickerWindow.kt — undecorated transparent always-on-top picker popup
  tray/TrayIconLoader.kt
  tray/MacOsAlwaysOnTopOverFullScreen.kt — JNA reflection helper (TD-1, see TECHDEBT)
```

## Requirements

- JDK **17** or higher
- macOS, Windows, or Linux (current functionality verified on macOS; Windows / Linux are tray-only stubs)
- Gradle is bundled via the wrapper — no local install needed

## Build & run

```bash
# Run the desktop app from gradle (development; tray icon + Settings window)
./gradlew :desktopApp:run

# Build everything (compile + tests + assemble)
./gradlew build

# Run shared-module tests
./gradlew :shared:jvmTest

# Run a single test class
./gradlew :shared:jvmTest --tests "dev.hackathon.linkopener.platform.macos.MacOsLinkLauncherTest"

# Coverage report (Kover)
./gradlew :shared:koverHtmlReport   # → shared/build/reports/kover/html/index.html

# Produce native distributables (DMG / MSI / DEB) for the current OS
./gradlew :desktopApp:packageDistributionForCurrentOS

# Just create the .app without DMG packaging (faster iteration on macOS)
./gradlew :desktopApp:createDistributable

# macOS only: build a signed DMG, submit to Apple notary, staple the ticket.
# Requires macos.signing.identity + macos.notarization.profile in ~/.gradle/gradle.properties.
./gradlew :desktopApp:stapleDmgViaKeychain
```

KMP test tasks are named after the target — `:shared:test` does **not** exist; use `:shared:jvmTest` (or `./gradlew test` / `./gradlew check` to run all tests across modules).

When `./gradlew :desktopApp:run` is up, look for the tray icon in your menu bar / system tray. The dev-only **Test picker** entry there spawns the popup with `https://example.com` so you can exercise the picker without packaging the app and registering it as the default browser. To go end-to-end (URL routed from the OS through us), see "Dev cycle for testing as the default browser" in `CLAUDE.md`.

## Tech stack

- **Kotlin** 2.3.20 (Multiplatform, JVM target only on this project)
- **Compose Multiplatform** 1.10.3 — Material 3 UI, Compose Resources for i18n
- **kotlinx.coroutines** 1.10.2 (with `kotlinx-coroutines-swing` for the JVM EDT dispatcher)
- **kotlinx.serialization** 1.10.0 — for persisted settings and the macOS plist JSON
- **multiplatform-settings** 1.3.0 — key/value settings storage (no-arg JVM impl)
- **JNA** — used only by the macOS NSWindow-level helper (see TD-1 in `TECHDEBT.md`)
- **Kover** 0.9.1 — coverage on `:shared`

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
- Platform-specific behavior is selected at runtime by `PlatformFactory.currentOs` rather than via expect/actual (all three OSes target the same JVM source set).

## Roadmap

The project was built incrementally in stages so multiple agents can work in parallel after the prototype stabilized the interfaces. Per-stage plans live in [`ai_stages/`](ai_stages/); the canonical status table is in [`CLAUDE.md`](CLAUDE.md).

**Closed:**
1. Prototype — tray + Settings skeleton, manual DI ✅
2. Browser discovery (macOS, filesystem + `plutil`) ✅
3a. Register as default-browser handler + receive URLs ✅
3 (close). `MacOsLinkLauncher` — real `open -a` ✅
4. Persistent settings (theme, language, autostart, exclusions) ✅
4.1. Default-browser detection (live via WatchService) + System Settings deep-link + sidebar refactor ✅
4.2. Browser picker popup (3 + "Show all", drag, scroll, dismiss-outside) ✅
4.5. Design system (Material 3 colors, typography, brand icon) ✅
5. i18n via Compose Resources XML + locale flip on click thread ✅

**Outstanding:**

6. Custom URL → browser rules
7. Windows support (registry, default-app registration)
8. Linux support (`.desktop` files, `xdg-mime`)

## Notes

- The tray icon is `desktopApp/src/jvmMain/resources/icons/app_icon.png` (brand asset, replaced the original placeholder during stage 4.5).
- Maven Central publishing has been removed — this repository is now an application, not a library.
