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

## TD-4 — macOS deep-link lands on wrong settings pane

**Where:** `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/macos/MacOsDefaultBrowserService.kt` (TODO comment in the same file).

**What:** Stage 4.1 wires "Open System Settings" to deep-link the user into the Default Web Browser dropdown. We tried `com.apple.preference.general` (lands on Appearance) and `com.apple.Desktop-Settings.extension` (lands on Wallpaper). Neither hits the Default Web Browser pane on Sonoma+. We currently route to the closest reachable pane and rely on the in-UI instructions to walk the user the rest of the way.

**Action:** dig out the right `x-apple.systempreferences:` URL for Sonoma+ Desktop & Dock → Default Web Browser. Apple has not published a stable list; most candidates need to be discovered empirically. Worst case — keep current behaviour and improve the inline copy.

---

## TD-6 — Inter font is not bundled

**Where:** `shared/src/commonMain/kotlin/dev/hackathon/linkopener/ui/theme/LinkOpenerTypography.kt` (TODO comment), context in `ai_stages/045_design_system/plan.md`.

**What:** Stage 4.5 design spec calls for Inter as the app font. We ship `FontFamily.Default` for now to avoid pulling in font binaries before we knew if the design would stick.

**Action:** drop Inter `.ttf` files into `shared/src/commonMain/composeResources/font/`, wire `Font(...)` in `LinkOpenerTypography.kt`, replace `FontFamily.Default` with the resulting `FontFamily(Font(...))`. Sub-1-hour change once the licensing is confirmed (Inter is OFL — license file lives next to the .ttf).

---

## TD-8 — macOS refresh button can show stale default-browser state

**Where:** `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/macos/MacOsDefaultBrowserService.kt` (`isDefaultBrowser`); the user-facing trigger is the Settings refresh button wired through `SettingsViewModel.refresh()`.

**What:** `isDefaultBrowser()` reads `~/Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist` via `plutil -convert json`. That `.plist` is *not* the live source of the default-browser binding — it is a snapshot of the Launch Services database that `lsd` (the Launch Services daemon) flushes to disk on its own schedule. Right after the user changes the default browser in System Settings, `lsd` updates its in-memory state instantly, but the on-disk `.plist` may not catch up for seconds (sometimes longer). Both the refresh button and the WatchService-driven live flow read the on-disk file, so both can report the previous value. We confirmed this empirically — switching to `defaults export <domain>` (which also goes through `cfprefsd`) returns the same stale data as `plutil`, ruling out cfprefsd caching as the culprit. The on-disk plist is just a stale snapshot of `lsd`.

**Why it's still in the repo:** every implementation that *does* see live state requires reaching past the .plist. Two viable approaches:

- **JNA → Launch Services API.** Call `LSCopyDefaultApplicationURLForURL(url, kLSRolesViewer, &err)` (or the newer `NSWorkspace.urlForApplicationToOpen:`) directly via JNA. We already depend on JNA. Cost: ~100–200 lines of bindings + manual `CFRelease` for `CFString`/`CFURL` returns + a small abstraction layer to keep the service unit-testable.
- **Bundled Swift helper.** Ship a tiny native binary inside `Contents/Resources/` of the `.app`, exec it from Kotlin, parse stdout. No JNA / no CF memory management, but adds a Swift step to the build pipeline and ~100 KB to the bundle.

The user-visible impact is "refresh button doesn't always reflect what System Settings just showed" — annoying but not load-bearing. Other paths (WatchService-driven live banner, picker behaviour, link-opening) all eventually catch up once `lsd` flushes.

**Action:** when this becomes load-bearing (release polish, user complaints), pick one of the two approaches above. Until then the limitation is documented and the existing read path is left as-is. Diagnostic hint for anyone re-investigating: `defaults export com.apple.LaunchServices/com.apple.launchservices.secure - | plutil -convert json -r -o - -` returns the same data as our current path, so don't switch to it expecting magic.

---

## TD-9 — Linux Chromium profile detection not implemented

**Where:** `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/linux/LinuxBrowserDiscovery.kt` (no profile expansion call), `ai_stages/08_linux_support/plan.md` § "Out of scope for stage 8".

**What:** macOS Chromium-family browsers expand into one Browser-per-profile during discovery (`MacOsBrowserDiscovery.expandWithProfiles` reads `~/Library/Application Support/Google/Chrome/Local State` JSON). On Linux every Chromium-family install surfaces as a single row even when the user has multiple profiles. The `LinuxLinkLauncher` already has the `--profile-directory=<id>` argv branch wired and would route correctly the moment we populate `Browser.profile`; what's missing is the scanner.

**Why it's still in the repo:** stage 8 was scoped to "browsers actually work on Linux" and per-profile UI was an explicit follow-up. Single-profile users — the majority — get a fully functional picker today.

**Action:** port `ChromiumProfileScanner` to read Linux paths (`~/.config/google-chrome/`, `~/.config/chromium/`, `~/.config/BraveSoftware/Brave-Browser/`, `~/.config/microsoft-edge/`, `~/.config/vivaldi/`). The `Local State` JSON layout is identical across OSes, so the parser itself ports unchanged — only the `chromiumUserDataPaths` map needs a Linux-flavoured analogue. Probably half a day including tests.

---

## TD-11 — `DiagnosticLog` has no automated tests

**Where:** `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/DiagnosticLog.kt`.

**What:** The `installEarly()` tee is install-once-and-forget glue that mirrors `System.out` / `System.err` to `~/.linkopener/last-run.log` so a double-click launch on Linux Mint still surfaces stderr to the user. It's not unit-tested — installing the tee mutates global state (`System.setOut` / `setErr`) and cleaning up properly inside a test would itself need most of the production tee plumbing. We've manually verified the tee survives `println` / `System.err.println` / Compose-EDT writes (the original implementation that subclassed PrintStream silently dropped most output, which is precisely what tests should catch).

**Action:** add a focused test that (a) creates a temp directory under `tmp.newFolder()`, (b) calls `installEarly()` with the directory injected, (c) writes via `println` / `System.err.println` from a few threads, (d) reads back the file and asserts every line is present. The injection point doesn't exist yet — `installEarly` builds the path internally — so adding a hook there is part of the work. ≈ 1–2 hours.

---

## How to add an entry

1. Pick the next free number (don't reuse retired ones — they're a stable handle once written).
2. Title: short, action-flavoured ("X is a stub", "Y deep-link lands on wrong pane"). Avoid vague titles like "Improve coverage".
3. **Where:** point at the file/line + the stage plan that recorded the issue.
4. **What:** what's broken / suboptimal *and why we did it that way* (the reason matters when the next person decides whether to fix).
5. **Action:** the smallest correct fix or the next investigation step.
6. If something becomes blocking, link the TD number from a code comment so the in-code TODO and this file stay in sync.
