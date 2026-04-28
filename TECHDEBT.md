# Tech debt

Consolidated list of known shortcuts, deferred work, and "we know, we'll fix it later" items in the repo. Each entry links to the code or stage plan where the issue lives, so anyone (human or agent) can pick one up cold without re-discovering the context. Update this file when:

- a new TODO is left in code that's larger than a one-line cleanup,
- a stage plan records a workaround it didn't get to fix,
- coverage / quality gates regress and we choose not to fix immediately.

Resolved items are removed (git log preserves history) — keep the list short so it stays readable.

---

## TD-1 — macOS picker doesn't overlay fullscreen apps

**Where:** `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/tray/MacOsAlwaysOnTopOverFullScreen.kt`, full investigation matrix in `ai_stages/042_browser_picker_popup/plan.md` § "TD-1".

**What:** The picker popup is `alwaysOnTop = true`, but on macOS Sonoma / Sequoia it still renders below windows in fullscreen Spaces. The current helper reaches the underlying `NSWindow*` via reflection (`sun.awt.AWTAccessor` → `sun.lwawt.LWWindowPeer` → `sun.lwawt.macosx.CFRetainedResource.ptr`) and calls `setLevel:` + `setCollectionBehavior:` through JNA. The JNA call succeeds (level / collectionBehavior are written), but visually it doesn't break through the fullscreen overlay. Suspected cause: Compose Desktop ships a regular `NSWindow`, and fullscreen overlay wants `NSPanel` with a non-activating styleMask.

**Why it's still in the repo:** the diagnostic logging is useful for the next attempt, the level/behavior writes are harmless, and the `--add-exports`/`--add-opens` plumbing is the same plumbing the migration target will need.

**Candidate next steps** (in increasing order of effort): isa-swizzling NSWindow → NSPanel; `orderFrontRegardless` + `setHidesOnDeactivate:NO`; build picker on a native NSPanel directly via JNA; accept the limitation and document it.

**When to act:** before public release / packaging for distribution. Until then the helper falls back gracefully on any reflection failure (logs, picker still renders, just under fullscreen).

---

## TD-2 — `IsDefaultBrowserUseCase` is dead code

**Where:** `shared/src/commonMain/kotlin/dev/hackathon/linkopener/domain/usecase/IsDefaultBrowserUseCase.kt`.

**What:** Stage 4.1 introduced `IsDefaultBrowserUseCase` as a one-shot probe. The `cd5fbef` polish replaced it with `ObserveIsDefaultBrowserUseCase` (a `Flow<Boolean>` driven by a `WatchService` against the LaunchServices plist), and nothing on the live graph references the original anymore (`AppContainer`, `SettingsViewModel`, all use the Observe variant). The class is still on disk because we didn't want to bundle the cleanup into an unrelated commit.

**Action:** delete `IsDefaultBrowserUseCase.kt` and any test that constructs it (the `SettingsViewModelTest` reference is to a fake VM constructor — verify that's the only thing left). Should be a 1-PR cleanup with no behavior change.

---

## TD-3 — UI-surface coverage gap

**Where:** Kover excludes list in `shared/build.gradle.kts`; numbers in `CLAUDE.md` § "Test coverage".

**What:** Class coverage on `:shared` slipped from a 92.5% line peak to 34.2% line / 75.7% class as new UI surface (BrowserPickerScreen, BrowsersState, NavSection variants, banner code, picker chrome) landed without unit tests. Most of that surface isn't on the Kover excludes list, so it counts against the report.

**Action:** decide per file whether each new piece is (a) intentional untested chrome (extend excludes, keep chrome as smoke-tested), or (b) genuinely worth a unit test (add one, possibly with compose-ui-test). Don't bulk-exclude — the value is in the case-by-case decision.

---

## TD-4 — macOS deep-link lands on wrong settings pane

**Where:** `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/macos/MacOsDefaultBrowserService.kt` (TODO comment in the same file).

**What:** Stage 4.1 wires "Open System Settings" to deep-link the user into the Default Web Browser dropdown. We tried `com.apple.preference.general` (lands on Appearance) and `com.apple.Desktop-Settings.extension` (lands on Wallpaper). Neither hits the Default Web Browser pane on Sonoma+. We currently route to the closest reachable pane and rely on the in-UI instructions to walk the user the rest of the way.

**Action:** dig out the right `x-apple.systempreferences:` URL for Sonoma+ Desktop & Dock → Default Web Browser. Apple has not published a stable list; most candidates need to be discovered empirically. Worst case — keep current behaviour and improve the inline copy.

---

## TD-5 — Windows default-browser detection is a stub

**Where:** `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsDefaultBrowserService.kt`.

**What:** `isDefaultBrowser()` always returns `false`. There is no real check against the registry (`HKCU\Software\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice` etc.). This is fine until stage 7 lands, but flagged so it doesn't get forgotten when Windows support is real.

**Action:** part of stage 7 anyway — leave the TODO in `WindowsDefaultBrowserService.kt` and treat this entry as a backlog cross-reference.

---

## TD-6 — Inter font is not bundled

**Where:** `shared/src/commonMain/kotlin/dev/hackathon/linkopener/ui/theme/LinkOpenerTypography.kt` (TODO comment), context in `ai_stages/045_design_system/plan.md`.

**What:** Stage 4.5 design spec calls for Inter as the app font. We ship `FontFamily.Default` for now to avoid pulling in font binaries before we knew if the design would stick.

**Action:** drop Inter `.ttf` files into `shared/src/commonMain/composeResources/font/`, wire `Font(...)` in `LinkOpenerTypography.kt`, replace `FontFamily.Default` with the resulting `FontFamily(Font(...))`. Sub-1-hour change once the licensing is confirmed (Inter is OFL — license file lives next to the .ttf).

---

## TD-7 — Dev-only "Test picker" tray entry must be removed before release

**Where:** `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/tray/TrayHost.kt:81` (TODO comment).

**What:** A "Test picker (dev)" item in the tray menu spawns the picker with `https://example.com/?utm=picker-test` so we can exercise the picker chain without packaging the app and registering it as the default browser. It's there because debugging the picker via the actual default-browser flow requires `createDistributable` + reinstall + Launch Services round-trip per change.

**Action:** remove the menu item before any public/packaged release. Optionally gate it behind `DEBUG_LOGGING` first (so internal builds keep it but stamped releases don't ship it) — the latter is a one-line guard.

---

## How to add an entry

1. Pick the next free number (don't reuse retired ones — they're a stable handle once written).
2. Title: short, action-flavoured ("X is a stub", "Y deep-link lands on wrong pane"). Avoid vague titles like "Improve coverage".
3. **Where:** point at the file/line + the stage plan that recorded the issue.
4. **What:** what's broken / suboptimal *and why we did it that way* (the reason matters when the next person decides whether to fix).
5. **Action:** the smallest correct fix or the next investigation step.
6. If something becomes blocking, link the TD number from a code comment so the in-code TODO and this file stay in sync.
