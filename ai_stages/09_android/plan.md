# Stage 09 — Android port

## Goal

Bring up `:androidApp` running the same picker / settings / rules-engine
flow we already have on macOS + Windows. Re-use `:shared` (the domain
layer + Compose UI is already in `commonMain`); only the per-OS platform
implementations need new Android variants.

## Strategy decisions

### 1. Android value proposition is *narrower* than desktop

Android already has a system-level browser picker for unmatched URLs
(activity chooser). Our differentiating fea­ture on Android is the
**rules engine** (per-host browser routing) — everything else (discovery,
default-browser bind, manifest registration) is provided by the OS.

We still ship the picker UI, settings, and full app shell — but the
"why install this" pitch on Android is rules. Keeps us honest about
scope: we don't need the full desktop polish to declare a usable port.

### 2. KMP target structure: `android()` on `:shared`, new `:androidApp` module

```
:shared
  ├─ commonMain    ← domain, UI, repository interfaces (already cross-OS)
  ├─ jvmMain       ← desktop-only platform impls (unchanged)
  └─ androidMain   ← NEW: Android platform impls

:desktopApp        ← unchanged
:androidApp        ← NEW: Activity, AndroidManifest, AppContainer
```

Alternatives considered:
- **Single `:composeApp` module with two targets**. Cleaner long-term,
  but invasive — we'd have to move `:desktopApp/AppContainer` /
  `:desktopApp/Main.kt` / tray code into a multiplatform module and risk
  destabilising the desktop build. Saved for a future refactor.
- **Separate `:androidShared`**. Adds layers without benefit.

### 3. `PlatformFactory` becomes `expect/actual`

Today `PlatformFactory` lives in `:shared/jvmMain` and dispatches by
`HostOs`. For Android we want a *different* factory entirely (Android
impls don't need OS detection — there's only one OS). Cleanest is to
declare an `expect` factory in `commonMain` and provide the actual
implementation in each target. Domain code already only references the
interfaces, so callers don't change.

### 4. Reuse Compose UI as-is, gate desktop-only widgets

`SettingsScreen`, `BrowserPickerScreen`, theme, icons, strings — all in
`commonMain`, all Material 3, all directly usable on Android. The
desktop-only bits to guard:
- `WindowDraggableArea` slot in `BrowserPickerScreen.headerWrapper` —
  already a slot, Android caller passes a no-op wrapper.
- `MacOsAlwaysOnTopOverFullScreen` — lives in `:desktopApp`, doesn't
  affect us.
- Tray — desktop-only, not invoked on Android.

### 5. `multiplatform-settings` and persistence

`com.russhwolf:multiplatform-settings-no-arg:1.3.0` supports Android
out of the box (uses `SharedPreferences`). On Android the `Settings()`
factory needs a `Context`; the `-no-arg` artifact handles this via a
content provider. No code changes in `:shared/data/SettingsRepositoryImpl`.

### 6. Entry shape on Android

URLs reach us via Android's Intent system, not via stdin/argv:

```
Browser app/launcher
  └─ Intent(ACTION_VIEW, http://...)
       └─ AndroidManifest declares we handle http/https
            └─ PickerActivity (Theme.Translucent, NoTitleBar)
                 └─ Compose host renders BrowserPickerScreen
                      └─ on selection: Intent.setPackage(target).startActivity()
                      └─ finish()
```

`PickerActivity` is `singleInstance` so the rapid-fire case (user clicks
multiple links) collapses into one activity instance per intent.

`MainActivity` (= settings) is a separate activity with its own
intent-filter for `ACTION_MAIN` / launcher icon — independent of the
picker path.

### 7. Out of scope (defer until v2)

- **Profile selection** for Chrome/Edge — Android Chrome doesn't expose
  per-user profiles via PackageManager; treat it as a single-profile app.
- **Manual browser addition** — `PackageManager` already enumerates every
  installed app; nothing to add manually. Keep `+ Add browser…` hidden
  on Android.
- **Autostart** — N/A, Android lifecycle is intent-driven.
- **Tray menu** — N/A, no system tray on Android.
- **i18n delta** — keep current locale set (en/ru); strings already in
  Compose Resources XML, picked up unchanged.
- **Wide-screen / landscape** layout polish — phone portrait is enough
  for v1.

## Phase plan

Numbered A1–A6 to match the stage 07 W-prefix scheme.

### A1 — Build infrastructure

- Add `com.android.tools.build:gradle` (AGP) to `gradle/libs.versions.toml`:
  - `agp = "8.7.3"` (latest stable compatible with Kotlin 2.3.x + Compose 1.11)
  - `android.application` and `android.library` plugin aliases.
- Root `build.gradle.kts`: add the two AGP plugins with `.apply(false)`.
- `settings.gradle.kts`: include `:androidApp`.
- Decide and document Android SDK install pathway (`local.properties`
  with `sdk.dir=...` — typical, not committed).

**Acceptance:** `./gradlew :shared:assemble` still green on macOS host
without Android SDK installed. Sync from Android Studio is fine.

### A2 — `:shared` android target

- Add `kotlin.android()` target with `compileSdk=35`, `minSdk=26`,
  `compilerOptions { jvmTarget = JvmTarget.JVM_17 }`.
- Apply `com.android.library` plugin to `:shared`.
- `androidMain` source set inheriting `commonMain` (default).
- Existing `jvmMain` stays jvm-only (desktop only).
- Refactor `PlatformFactory`:
  - `expect class PlatformFactory(...)` in `commonMain` (or factory
    methods).
  - Move JVM impl (the macOS/Windows/Linux dispatch) to `jvmMain`.
  - Add stub `actual` in `androidMain` returning unimplemented platform
    services for now (next phase fills them in).

**Acceptance:** `:shared:compileKotlinAndroid` succeeds. `:shared:jvmTest`
still passes.

### A3 — Android platform impls

| Interface | Android implementation |
| --- | --- |
| `BrowserDiscovery` | `PackageManager.queryIntentActivities(Intent(ACTION_VIEW, "http://example.com"))`. ResolveInfo → label, package name, icon (deferred to A5). Filter ourselves by package id. |
| `LinkLauncher` | `Intent(ACTION_VIEW, uri).setPackage(target.applicationPath).also { context.startActivity(it.addFlag(FLAG_ACTIVITY_NEW_TASK)) }`. `applicationPath` carries the package id on Android (we'll alias that field's interpretation per OS). |
| `UrlReceiver` | Custom — emits a `Flow<String>` driven by `PickerActivity` calling `intent.dataString` on each new intent. Different shape from JVM stdin reader; abstract via the existing interface. |
| `DefaultBrowserService` | `RoleManager.isRoleHeld(ROLE_BROWSER)` (API 29+). System-settings deep-link via `Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)`. Live observation: poll on `onResume` (registry watcher pattern would need a permission). |
| `AutoStartManager` | No-op (delegates to `NoOpAutoStartManager` which already exists). |
| `ManualBrowserExtractor` | `UnsupportedManualBrowserExtractor` (existing) — Android doesn't need it. |

The big subtlety: `Browser.applicationPath` on desktop is a filesystem
path; on Android it's a package id. We can keep the field name and treat
it as an opaque "launcher token" — the `LinkLauncher` impl knows how to
interpret it for its OS.

**Acceptance:** Unit tests against scripted PackageManager fakes
(`shadowOf(context.packageManager)` from Robolectric, or hand-rolled
fakes — Robolectric is heavy, prefer hand-rolled).

### A4 — `:androidApp` module

- New module `:androidApp` with `com.android.application` plugin +
  `kotlin.multiplatform` (jvmMain depends on shared:android).
  Actually simpler: `:androidApp` is a **single-target Android module**
  (not multiplatform) that depends on `shared`'s android variant.
- `AndroidManifest.xml`:
  - `MainActivity` — launcher intent (ACTION_MAIN + LAUNCHER).
  - `PickerActivity` — `singleInstance`, Theme.Translucent, intent-filter
    for `ACTION_VIEW` http/https.
- `AndroidAppContainer` — manual DI mirror of `AppContainer`, passes a
  `Context` into the platform factory.
- `MainActivity` hosts `SettingsScreen` via `setContent { … }`.
- `PickerActivity` hosts `BrowserPickerScreen` via `setContent { … }`,
  reads `intent.dataString`, calls `linkLauncher.launch(...)` on
  selection, then `finish()`.

**Acceptance:** Install on emulator, set as default browser via system
settings, tap a link in any app → picker appears, selecting Chrome
opens it.

### A5 — Polish: icons + first-run UX

- Browser icons: `ResolveInfo.loadIcon(packageManager)` → `Drawable` →
  `ImageBitmap`. Cache by package id in `BrowserRepositoryImpl` (already
  has caching seam). Need to add an optional icon field on `Browser`
  and a per-OS icon loader interface (`MacOsBrowserIconLoader` already
  reads `.icns` — symmetry).
- First-run: when user opens app, if `RoleManager.isRoleHeld` is false,
  prompt with `RoleManager.createRequestRoleIntent(ROLE_BROWSER)`.
- App icon: reuse `app_logo_v2.svg` from `:shared/composeResources`,
  generate adaptive-icon XML pointing at it.

**Acceptance:** Visual parity with the desktop picker (same icons,
roughly the same layout in portrait).

### A6 — Tests

- `:shared:androidUnitTest` — port a subset of `:shared:jvmTest`
  (the platform-agnostic ones: `RuleEngineTest`, `HostGlobMatcherTest`,
  `BrowserOrderingTest`, etc. — they already live in `commonTest`,
  should run automatically once we add the target).
- New `AndroidBrowserDiscoveryTest` against fake `PackageManager`.
- New `AndroidLinkLauncherTest` against captured `Intent`s.
- Skip integration / Espresso UI tests for v1.

**Acceptance:** `./gradlew :shared:check` green for both JVM and Android.

## Open questions

1. **Compose runtime divergence.** `compose.material3` from the version
   catalog is `org.jetbrains.compose.material3:material3:1.11.0-alpha07`.
   Verify that the multiplatform artifact resolves correctly on the
   android target and produces a working M3 theme on Android.
2. **Compose Resources on Android.** Our `composeResources/` directory
   stores SVGs. Compose Multiplatform 1.11 supports SVG on Android via
   Skia/skiko, but verify the actual rasterisation path. If it breaks,
   fallback is to generate per-density PNGs.
3. **`multiplatform-settings-no-arg` on first run before the content
   provider initialises.** Defensive: lazy-initialise our settings
   repo, not eager.
4. **Role binding UX on Android 14+.** Newer Android versions made
   default-browser binding stricter; `createRequestRoleIntent` may
   require additional manifest permissions or no longer prompt at all.
   Test on emulator running API 34/35.
5. **Intent looping.** `PickerActivity` launches another browser via
   intent — if our manifest matches `http`/`https` and we don't filter
   ourselves out before calling `setPackage`, we could route the URL
   back to ourselves. Need an explicit self-package guard in
   `AndroidLinkLauncher`.

## Out of scope explicitly

- Tablet / foldable layout adaptation
- Per-profile routing for Chrome/Edge on Android
- Wear OS / Android TV
- Play Store packaging + Bundle signing — APK side-load only for v1
- Gradle native distribution (no equivalent to packageMsi for Android)
- Notification / badge for picker — phone has no need
- Foreground service for any reason

## Estimated complexity

| Phase | Scale |
| --- | --- |
| A1 — Build infra | S — config only |
| A2 — `:shared` android target | M — expect/actual refactor of PlatformFactory |
| A3 — Android platform impls | M — 4 thin classes + tests |
| A4 — `:androidApp` module | M — Activity wiring + manifest |
| A5 — Polish | M — icon plumbing across OSes |
| A6 — Tests | S — most exists in commonTest |

Total: feasible in a focused stage. Bigger risk is build infra
(AGP/Kotlin/Compose version compatibility) than code volume.
