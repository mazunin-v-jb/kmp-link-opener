# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A cross-platform Compose Desktop application (macOS / Windows / Linux) that registers itself as a browser and lets the user pick which browser opens each clicked link. The macOS path is fully wired end-to-end (registration → URL receive → picker UI → real `open -a` launcher); Windows / Linux are stubs awaiting per-OS discovery + launcher work in stages 7/8. See `ai_stages/00_overview.md` for the roadmap and `ai_stages/<NN>_<stage>/plan.md` for per-stage plans.

Status as of latest main:

| Stage | What | macOS |
| --- | --- | --- |
| 1 | Tray + Settings skeleton, DI | ✅ |
| 2 | Browser discovery via filesystem + `plutil` | ✅ |
| 3a | Register as default-browser handler, receive URLs | ✅ |
| 3 (close) | `MacOsLinkLauncher` — real `open -a` | ✅ |
| 4 | Settings persistence (theme/lang/autostart/exclusions) | ✅ |
| 4.1 | Default-browser detection + System Settings deep-link | ✅ (real plist read) |
| 4.2 | Browser picker popup window | ✅ |
| 4.5 | Design system (colors, typography, icons, theme) | ✅ |

## Workflow rules (from `prompts/1_Prompt.md`)

- **Per-feature branch.** New work goes in its own branch off `main`.
- **After each iteration:** run all tests, build the artifact, commit to the current branch. **Do not push** — pushing is the user's call.
- **Tests on every change.** Keep `./gradlew build` green; add tests for new functionality.
- **Placeholder icons:** mark with `TODO` and call out in chat. Stage 4.5 replaced the original placeholder with `app_icon.png`; the tray loader is in `desktopApp/.../tray/TrayIconLoader.kt`.
- **Sensitive data — STOP AND ASK.** Signing identities, app-specific passwords, etc. live in `~/.gradle/gradle.properties` (outside the repo). The notarization keychain profile is created once via `xcrun notarytool store-credentials` and referenced by name only. Never commit secrets.
- For each new stage, create `ai_stages/<NN>_<name>/plan.md` describing the implementation in detail before coding.

## Build & test commands

```bash
# Build everything (compile + tests + assemble)
./gradlew build

# Run the desktop app from gradle (development; tray icon + Settings window)
./gradlew :desktopApp:run

# Run shared-module tests
./gradlew :shared:jvmTest

# Run a single test class
./gradlew :shared:jvmTest --tests "dev.hackathon.linkopener.platform.macos.MacOsLinkLauncherTest"

# Produce native distributable for the current OS (DMG on macOS, MSI on Windows, DEB on Linux)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Just create the .app without DMG packaging (faster iteration on macOS)
./gradlew :desktopApp:createDistributable

# macOS only: build a signed DMG, submit to Apple notary, staple the ticket.
# Requires macos.signing.identity and macos.notarization.profile in ~/.gradle/gradle.properties
# (see "Native distributables" below).
./gradlew :desktopApp:stapleDmgViaKeychain

# Coverage (Kotlinx Kover)
./gradlew :shared:koverHtmlReport   # → shared/build/reports/kover/html/index.html
./gradlew :shared:koverXmlReport    # for tooling
```

KMP test tasks are named after the target — `:shared:test` does **not** exist; use `:shared:jvmTest` (or `./gradlew test`/`./gradlew check` to run all tests across modules).

## Debug logging

`AppContainer` does a warm-up `DiscoverBrowsersUseCase()` at startup so the picker is instant on first invocation; the result is cached in `BrowserRepositoryImpl`. The discovery dump (`Discovered N browser(s): …`) only prints when `-Dlinkopener.debug=true` is on the JVM args. Add it to the IDE run config, or pass via `./gradlew :desktopApp:run -Dlinkopener.debug=true` (or `JAVA_OPTS`/`-X` flags when launching the packaged `.app`). Errors surface to stderr regardless.

## Dev cycle for testing as the default browser

`./gradlew :desktopApp:run` only spins up the JAR — macOS will not include it in the Default Web Browser dropdown because the Info.plist from `nativeDistributions` lives only in the packaged `.app`. To exercise the registration / URL-receive / launcher path:

1. `./gradlew :desktopApp:createDistributable` (or `packageDmg` if you also want the DMG / notarization).
2. Replace `/Applications/Link Opener.app` with the freshly built one.
3. Open the app from Finder once so Launch Services indexes it.
4. System Settings → Desktop & Dock → Default web browser → pick "Link Opener".
5. From any terminal: `open https://example.com` — the picker should appear; clicking a browser hands the URL off via `open -a`.

## Architecture

Two Gradle modules:

- **`:shared`** — KMP library, JVM target only. Domain models, use cases, repositories, platform interfaces, Compose UI. Source sets: `commonMain`, `commonTest`, `jvmMain`, `jvmTest`. Package root: `dev.hackathon.linkopener`.
- **`:desktopApp`** — runnable Compose Desktop application. `main()`, the manual composition root (`AppContainer`), the system tray host, and the picker window. Depends on `:shared`. Package root: `dev.hackathon.linkopener.app`.

Layered structure inside `:shared`:

```
core/      — domain models (Browser, AppSettings, AppTheme, BrowserId, ...)
domain/    — repository interfaces + use cases (operator fun invoke; Discover/Update/SetEtc.)
data/      — repository implementations (multiplatform-settings on JVM)
platform/  — interfaces (BrowserDiscovery, UrlReceiver, LinkLauncher,
             DefaultBrowserService, AutoStartManager) with jvmMain impls
             picked at runtime by inspecting `PlatformFactory.currentOs`
ui/        — Compose surface: settings screen, browser picker, theme,
             design system (icons / colors / typography), tray menu types
```

No DI framework — `AppContainer` wires the graph by hand. Platform-specific behavior is selected at runtime, not via expect/actual (all three OSes target the same JVM source set).

`PlatformFactory.createX()` for X ∈ {AutoStartManager, BrowserDiscovery, UrlReceiver, DefaultBrowserService, LinkLauncher} dispatches on `currentOs: HostOs`. Non-mac platforms fall back to no-op / printing implementations until the dedicated platform stages land (7 = Windows, 8 = Linux).

## Dependencies

Versions are managed in `gradle/libs.versions.toml`. Compose Multiplatform components are pulled via the Compose plugin's `compose.runtime` / `compose.foundation` / `compose.components.resources` accessors at version `1.10.3`. Material 3 is now declared as a direct dependency from the version catalog (see "Polish: material3" below) instead of the plugin shorthand to silence the deprecation warning.

Other notable deps: `kotlinx-coroutines` (with `kotlinx-coroutines-swing` for the JVM EDT dispatcher), `kotlinx-serialization-json` (used by `SettingsRepositoryImpl` and the macOS plist parsers), `multiplatform-settings-no-arg` (for persistence), Kover `0.9.1` (for coverage on `:shared`).

## Native distributables (macOS specifics)

- `compose.desktop.application.nativeDistributions.packageVersion` must be `MAJOR.MINOR.PATCH` with `MAJOR > 0` (the DMG packager rejects `0.x.y`). The internal `AppInfo` version (returned by `GetAppInfoUseCase`) is independent of the packaging version and can stay at `0.1.0`.
- Bundle id is `dev.hackathon.linkopener` and is duplicated in two places — `desktopApp/build.gradle.kts` (`nativeDistributions.macOS.bundleID`) and `AppContainer.ownBundleId`. Keep them in sync; the latter is used both to filter ourselves out of the picker and to recognize ourselves in the LaunchServices plist.
- `Info.plist` extras (declared via `extraKeysRawXml`):
  - `CFBundleURLTypes` for `http`/`https` with `LSHandlerRank=Default`,
  - `CFBundleDocumentTypes` for `public.html` / `public.xhtml`,
  - `ASWebAuthenticationSessionWebBrowserSupportCapabilities` (the modern macOS Sonoma+ signal that we're a real browser — without this the System Settings picker hides us even though Launch Services routes URLs to us),
  - `CFBundleDisplayName` for a clean name in the dropdown.
- Codesigning + notarization are gated behind two gradle properties (read from `~/.gradle/gradle.properties`):
  - `macos.signing.identity` — the Developer ID Application certificate, e.g. `Developer ID Application: <Name> (<TEAMID>)`. Find via `security find-identity -p codesigning -v`.
  - `macos.notarization.profile` — the name of a `notarytool` keychain profile created once via `xcrun notarytool store-credentials <profile> --apple-id <...> --team-id <...> --password <app-specific-password>`.
  - Both unset → unsigned, unnotarized `.app`. `packageDmg` still works; install via right-click → Open.
- Custom gradle tasks: `notarizeDmgViaKeychain` (runs `xcrun notarytool submit … --keychain-profile`) and `stapleDmgViaKeychain` (chains `packageDmg` → `notarizeDmgViaKeychain` → `xcrun stapler staple`). Compose's built-in `notarizeDmg` is not used because it doesn't expose `--keychain-profile`.

## Test coverage

Kover is wired on `:shared`. Several classes are explicitly excluded because they're process-spawning / framework-glue layers that we'd only smoke-test on the matching OS:

- `JvmUrlReceiver` (wraps `Desktop.setOpenURIHandler`)
- `PlutilRunner` (`ProcessBuilder` to `/usr/bin/plutil`)
- `MacOsAutoStartManager` (writes a LaunchAgent plist)
- `MacOsDefaultBrowserService` and `WindowsDefaultBrowserService` (deep-link to System Settings via `open` / `cmd /c start`)
- A few stage 4.5 UI scaffolding classes (Compose UI is exercised by the design system, not unit-tested)

The Linux default-browser service IS unit-tested because it's a no-op stub. Smoke tests for macOS-only logic guard themselves with `Assume.assumeTrue("…", isMacOs)` so jvmTest stays portable.

## Things to leave alone

- The repository was originally scaffolded as a Maven Central library template; that publishing config has been removed and **should not be re-added** unless the project is consciously turned back into a library.
- `prompts/1_Prompt.md` is the user's source-of-truth requirement spec — read it for context, but only edit it if the user explicitly asks.
- The `LinkLauncher` interface and the picker chain (`PickerCoordinator`, `PickerWindow`, `BrowserPickerScreen`, `PrintingLinkLauncher`) are stage 4.2 territory and are stable. Don't redesign — add per-OS launchers next to `MacOsLinkLauncher` instead.
