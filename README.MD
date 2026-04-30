# Link Opener

A cross-platform desktop browser-picker. Set Link Opener as the system's default browser; every clicked link pops up a small chooser, and you decide on the spot which browser opens it (Chrome / Firefox / Safari / Vivaldi / Brave / Edge / Opera / …). Optional rules route specific hosts to specific browsers automatically. Compose for Desktop on macOS / Windows / Linux, plus an Android share-target build.

## Status

| Platform | Discovery | Picker | Default-browser binding | Auto-start | Tray icon |
| --- | --- | --- | --- | --- | --- |
| macOS | ✅ filesystem + plutil | ✅ overlays normal windows[¹](#tech-debt-and-known-limitations) | ✅ `Info.plist` URL types lit by Launch Services | ✅ LaunchAgent | ✅ AWT, template-image autotint |
| Windows | ✅ HKLM/HKCU `StartMenuInternet` | ✅ | ✅ HKLM/HKCU registry; MSI patches `App Paths` for the Win11 UserChoice hash | ✅ HKCU `Run` | ✅ AWT |
| Linux | ✅ XDG `.desktop` walker | ✅ | ✅ writes `~/.local/share/applications/link-opener.desktop`; user picks via System Settings (Chrome's exact convention — see `shell_integration_linux.cc`) | ✅ `~/.config/autostart/*.desktop` | ✅ libayatana-appindicator via [ComposeNativeTray](https://github.com/kdroidFilter/ComposeNativeTray) |
| Android | n/a | ✅ ACTION_SEND share target | RoleManager prompt | n/a | n/a |

[¹] One known macOS limitation: the picker doesn't pierce fullscreen Spaces — see [TECHDEBT.md TD-1](TECHDEBT.md).

## Install

Pre-built artifacts ship with each tagged release on [GitHub Releases](https://github.com/mazunin-v-jb/kmp-link-opener/releases).

- **macOS** — `Link Opener-X.Y.Z.dmg`. Drag into `/Applications`, open once, then System Settings → Desktop & Dock → Default web browser → Link Opener. Builds are unsigned; right-click → Open the first time to satisfy Gatekeeper.
- **Windows** — `Link Opener-X.Y.Z.msi`. Double-click. Settings → Apps → Default apps → Web browser → Link Opener.
- **Linux** — `link-opener-X.Y.Z-linux-x64.jar` fat-JAR runnable on glibc 2.31+ x86_64. Needs JDK 17+ (`sudo apt install openjdk-17-jre` on Mint / Ubuntu). On Cinnamon / GNOME / KDE the tray icon goes through `libayatana-appindicator` (preinstalled on Mint 21+). After first launch, pick "Link Opener" via System Settings → Default Applications → Web. Run with:

  ```bash
  java -jar link-opener-X.Y.Z-linux-x64.jar
  ```
- **Android** — `link-opener-X.Y.Z.apk`, sideload. Acts as an `ACTION_SEND` / `ACTION_VIEW` target and surfaces a chooser per link.

## Build

Single source of truth for the version is `linkopener.version` in [`gradle.properties`](gradle.properties); both DMG metadata and the runtime `AppInfo.version` (Settings sidebar) read from it. Format `MAJOR.MINOR.PATCH`, `MAJOR ≥ 1` (the DMG packager rejects `0.x.y`). Bump before pushing to `main`.

```bash
./gradlew build                               # compile + tests + assemble (all targets)
./gradlew :desktopApp:run                     # dev run (tray + Settings)
./gradlew check                               # full test pass on every module
./gradlew :shared:jvmTest                     # shared module JVM tests only
./gradlew :shared:koverHtmlReport             # coverage → shared/build/reports/kover/html/

./gradlew :desktopApp:packageDmg              # macOS DMG (jpackage)
./gradlew :desktopApp:patchMsiHklm            # Windows MSI + HKLM browser-registration patch
./gradlew :desktopApp:packageLinuxUberJar     # Linux fat-JAR (cross-compilable from any host)
./gradlew :desktopApp:packageWindowsUberJar   # Windows fat-JAR (no installer; testing on a host without WiX)
./gradlew :androidApp:assembleRelease         # Android APK
./gradlew :desktopApp:stapleDmgViaKeychain    # macOS only: signed + notarized + stapled DMG
                                              # (needs macos.signing.identity + macos.notarization.profile
                                              #  in ~/.gradle/gradle.properties)
```

A debug toggle is wired through the `linkopener.debug` JVM system property — it gates the startup discovery dump and the rules-engine match trace; see `CLAUDE.md` § "Debug logging". The desktop app also tees `System.out` / `System.err` to `~/.linkopener/last-run.log` every startup, so a double-click launch with no terminal still leaves a diagnostic trail (Linux Mint usability fix from stage 8).

## Architecture

Two Gradle modules:

- **`:shared`** — KMP library targeting JVM (macOS / Windows / Linux desktop) + Android. Domain models, repositories, use cases, platform interfaces, Compose UI. Source sets: `commonMain` / `commonTest` / `jvmMain` / `jvmTest` / `androidMain`.
- **`:desktopApp`** — runnable Compose Desktop application: `main()`, single-instance enforcement, the `AppContainer` composition root, the system-tray host, the picker window, and the per-OS boot glue (Linux `.desktop` registration, Windows HKCU registration).

Per-OS dispatch is driven at runtime by `PlatformFactory.currentOs` rather than expect/actual — every platform compiles into the same JVM source set. The architecture deep-dive (layer diagram, manual DI wiring, dispatch table) lives in [`CLAUDE.md`](CLAUDE.md).

Per-feature implementation plans live under [`ai_stages/`](ai_stages/). Stage 8 (Linux support, including the libayatana-appindicator tray) is at [`ai_stages/08_linux_support/plan.md`](ai_stages/08_linux_support/plan.md). Stage 7 (Windows MSI + HKLM patch + UserChoice hash dance) at [`ai_stages/07_windows_support/`](ai_stages/07_windows_support/).

## Test coverage

Kover totals (live, regenerated by `./gradlew :shared:koverHtmlReport`):

| Metric | Coverage |
| --- | --- |
| Class | 88.3% (158/179) |
| Method | 84.5% (344/407) |
| Line | 82.7% (1321/1598) |
| Instruction | 79.9% (12040/15063) |
| Branch | 63.6% (738/1161) |

Test counts: **461 JVM tests in `:shared`** + **38 in `:desktopApp`**, ≈ 500 total.

The Kover filter excludes the manually-tested Compose UI surface (Settings screens / picker screen / icon definitions / theme tokens), Compose Resources' generated accessor classes, and ProcessBuilder-only platform glue smoke-tested on the matching OS — full exclusion list in `shared/build.gradle.kts` § `kover.reports.filters.excludes`. The Branch column trails because every `when (HostOs)` compiles to a multi-arm switch and the platform arms that don't fire on the test host stay uncovered (covered branches are the active arm + guard rails).

Refresh:

```bash
./gradlew :shared:koverHtmlReport
open shared/build/reports/kover/html/index.html   # macOS
```

## Tech debt and known limitations

Tracked centrally in [`TECHDEBT.md`](TECHDEBT.md). Each entry has a stable identifier (`TD-N`), a `Where:` pointer at the relevant code or stage plan, a `What:` describing why we did it the way we did, and an `Action:` with the smallest reasonable next step. Add an entry for any "we know, we'll fix it later" item larger than a one-line cleanup.

## Contributing notes

- Per-feature branch off `main`. Each iteration: run all tests + build the artifact + commit. **Don't push** without explicit OK — pushing is the maintainer's call.
- **Always bump `linkopener.version`** before pushing to `main`. Same rule on conflict resolution: pick the higher of both sides and add `+0.0.1`.
- For each new stage, drop a `ai_stages/<NN>_<name>/plan.md` describing the implementation in detail before writing code.
- Sensitive material (signing identity, app-specific passwords, notarization keychain profiles) lives in `~/.gradle/gradle.properties`, never in the repo.

## License

Hackathon project — intentionally no formal license declared. Treat as "all rights reserved" until that changes.
