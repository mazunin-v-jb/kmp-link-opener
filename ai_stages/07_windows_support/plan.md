# Stage 07 — Windows support

Status: **draft / awaiting decisions**.

This stage takes the app from "Windows runs as a no-op stub" to "Windows
behaves like macOS does today": the picker shows real installed
browsers, picking a row launches that browser with the URL, the
default-browser banner reflects reality, autostart works, and a
packaged MSI registers the app as a URL handler so Windows routes HTTP
links into our `pickerCoordinator`.

The cross-platform interfaces in `commonMain` (`BrowserDiscovery`,
`LinkLauncher`, `DefaultBrowserService`, `AutoStartManager`,
`UrlReceiver`, `BrowserMetadataExtractor`) are already in place — this
stage adds Windows-targeted implementations and wires them into
`PlatformFactory`. No domain or UI changes are required.

**Non-goals:**
- Touching the macOS code paths.
- Linux (that's stage 8).
- New features beyond parity with macOS.
- Compose UI changes.

---

## 0. What's there today

| Capability | Today | Stage-7 target |
| --- | --- | --- |
| Browser discovery | `EmptyBrowserDiscovery` returns `[]` | Real list from registry + filesystem |
| Link launcher | `PrintingLinkLauncher` writes to stdout | `ProcessBuilder(<exe>, <url>)` |
| Default-browser status | `WindowsDefaultBrowserService.isDefaultBrowser` returns `false` always | Read `HKCU\…\UserChoice\ProgId` |
| Open System Settings | ✅ works (`ms-settings:defaultapps`) | unchanged |
| Auto-start at login | `NoOpAutoStartManager` | Toggle `HKCU\…\Run\LinkOpener` |
| URL receive (HTTP/HTTPS) | App never gets URLs from Windows | MSI registers ProgId; runtime reads CLI argv |
| Manual `.exe` addition | `UnsupportedManualBrowserExtractor` | Parse `.exe` version-info resource |
| Browser icon resolution | (depends on stage 8/design-refresh) | Defer — out of scope here |

---

## 1. Implementation strategy — pick one

Two ways to talk to the Windows Registry from a JVM app:

**A. Shell out to `reg.exe`.**
- One subprocess per query (~50ms).
- No native plumbing; same pattern as macOS's `plutil`, `sips`, `open`.
- All the parsing happens in Kotlin against `reg.exe`'s text output.
- Read-only operations are trivial; writes (autostart, ProgId)
  also work for HKCU without elevation.
- Used by tools like Wineskin, JetBrains Toolbox, Discord installer.

**B. JNA via `com.sun.jna.platform.win32.Advapi32`.**
- In-process, no subprocess overhead.
- Strongly typed but verbose (`HKEY` handles, `RegOpenKeyEx`,
  `RegQueryValueEx`, `Memory`, `IntByReference`, `Native.toString`...).
- We'd add `net.java.dev.jna:jna-platform` (~1.5MB) to dependencies;
  base `jna` is already present (used by
  `MacOsAlwaysOnTopOverFullScreen`).

**Recommendation: A**, mirroring the existing macOS pattern. The
parsing layer becomes trivially testable against captured `reg.exe`
output strings (same shape as the existing `PlistJsonParserTest`
fixtures). Switch to **B** later only if performance becomes a problem,
which it won't for a tool that queries the registry once at startup
and on user actions.

---

## 2. Decision questions for scope

Before coding I want to land:

1. **Implementation strategy** — A (reg.exe) or B (JNA)? See §1.
2. **Scope of this stage**:
   - **Minimum**: discovery + launcher. Picker works end-to-end on
     Windows for whatever browsers Windows already has registered, but
     the user has to manually copy URLs (because no URL handler
     registration yet).
   - **Mid**: + default-browser detection + autostart. Picker still
     can't *receive* links from Windows, but the rest of the UI
     reflects Windows reality.
   - **Full**: + ProgId registration in MSI installer + URL receive.
     End-to-end: user clicks a link in any app, it pops our picker.
3. **Whether to attempt MSI changes** in `desktopApp/build.gradle.kts`
   in this stage. Compose Desktop's `nativeDistributions.windows`
   block already exists; adding ProgId registration is some careful
   `iconFile` / `dirChooser` / `installationPath` gymnastics + a
   `extraInstallerFlags = listOf("--registry-value", ...)` style
   workaround.
4. **How to verify**. I'm on macOS — I can write the code,
   compile, and add unit tests against captured `reg.exe` fixtures. End-
   to-end smoke tests on a real Windows host are out of my reach in
   this session; user runs them.

---

## 3. Implementation phases

Numbered W1, W2, … for cross-reference. Each phase is independently
shippable (tests + docs); we ship as many as scope allows.

### W1. Windows browser discovery

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/RegistryReader.kt` (new)
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsBrowserDiscovery.kt` (new)
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/PlatformFactory.kt` (wire it)

**Approach**: enumerate
`HKLM\SOFTWARE\Clients\StartMenuInternet` and
`HKCU\SOFTWARE\Clients\StartMenuInternet`. Each subkey is a browser:

```
HKLM\SOFTWARE\Clients\StartMenuInternet\Google Chrome
    (default) = "Google Chrome"
    \Capabilities
        ApplicationName = "Google Chrome"
        ApplicationDescription = "Access the Internet"
    \shell\open\command
        (default) = "C:\Program Files\Google\Chrome\Application\chrome.exe"
```

For each subkey we read the display name, the `shell\open\command`
exe path (strip surrounding quotes + `--` flags), and version via
the file's `VersionInfo` (a separate `reg.exe`-free read of the .exe
PE header — phase W7 if scope allows; for now just leave version null).

**`reg.exe` semantics**:
```
reg query "HKLM\SOFTWARE\Clients\StartMenuInternet" /s /v ""
```
returns subkey enumeration; parse the text. We can also use
`reg query <subkey>` to read individual values.

**Tests** (`commonTest`-style, against captured fixture strings):
- happy path: 3 browsers (Chrome, Firefox, Edge)
- one subkey without `shell\open\command` → skipped, others survive
- `shell\open\command` value with quotes around path
- `shell\open\command` value with trailing `-- "%1"` argv that we strip

### W2. Windows link launcher

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsLinkLauncher.kt` (new)

**Approach**: `ProcessBuilder(<exe>, <url>).start()`. Most browsers
take URL as first argv. Chromium-family additionally accept
`--profile-directory=<id>` if `Browser.profile != null` — same flag
as macOS path, just argv order differs (no `--args` separator
needed).

**Tests**: against a fake `processFactory` that records the argv
list. Verify the URL is appended; verify the profile flag is
inserted for Chromium browsers.

### W3. Default-browser detection (read-only)

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsDefaultBrowserService.kt` (extend existing)

**Approach**:
```
reg query "HKCU\SOFTWARE\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice" /v ProgId
```
returns a string like `ChromeHTML` or our app's ProgId after we
register it. Compare against our own ProgId (= `LinkOpener.URL`).
While we don't have a registered ProgId yet (no MSI), this returns
whatever the system has set — which is what the UI banner needs.

**Note**: `observeIsDefaultBrowser` stays one-shot for now (the
default-impl in `DefaultBrowserService`). Live-watching the registry
is JNA territory — punt to a follow-up. Manual refresh button (TD-8)
already covers that gap.

### W4. Auto-start at login

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsAutoStartManager.kt` (new)

**Approach**:
```
reg add "HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" \
    /v "LinkOpener" /t REG_SZ \
    /d "\"C:\Path\To\LinkOpener.exe\"" /f
```
to enable; `reg delete ... /v "LinkOpener" /f` to disable. The exe
path comes from `ProcessHandle.current().info().command()`.

**Tests**: against a fake `processFactory` recording argv lists.
Verify enable / disable / idempotent re-enable.

### W5. URL receive — ProgId registration in MSI 🟠 deeper work

**Files**:
- `desktopApp/build.gradle.kts` (extend `nativeDistributions.windows`)
- A `windows/registry.wxi` snippet or post-install script

**Approach**: Compose Desktop builds the MSI via WiX under the hood.
We need WiX to register:
- `HKLM\SOFTWARE\RegisteredApplications` `LinkOpener` →
  `Software\Clients\StartMenuInternet\LinkOpener\Capabilities`
- `HKLM\SOFTWARE\Clients\StartMenuInternet\LinkOpener\Capabilities\URLAssociations`
  `http`/`https` → `LinkOpener.URL`
- `HKLM\SOFTWARE\Classes\LinkOpener.URL\shell\open\command`
  → our exe + `"%1"`

Compose Desktop's gradle DSL doesn't expose raw WiX directly. Three
paths:
- **5a. `extraInstallerFlags`** to inject WiX fragments at MSI build
  time. Possible but underdocumented.
- **5b. Replace the Compose-generated MSI step** with a custom
  WiX-direct task. Higher effort, more control.
- **5c. Skip MSI registration in this stage**; ship binary that
  Windows can install but doesn't auto-receive HTTPS URLs. User has
  to manually invoke or use `start linkopener.exe URL`. Test mode
  through the existing `tray_menu_test_picker` already covers
  development.

**Recommendation**: **5c** for now. The other capabilities (W1–W4)
are valuable on their own; URL receive needs careful WiX work that
deserves its own iteration.

### W6. Runtime CLI argv → URL receive

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/JvmUrlReceiver.kt` (extend)
- `desktopApp/src/jvmMain/kotlin/dev/hackathon/linkopener/app/Main.kt` (read argv, push to UrlReceiver before tray starts)

**Approach**: Once W5 lands, Windows will spawn `LinkOpener.exe
<url>` for each link click. Read `args` in `main()`; if non-empty,
forward the first non-flag arg through `pickerCoordinator.handleIncomingUrl`.

**Note**: this also needs to interact with `SingleInstanceGuard`. If
a primary instance is already running, the secondary should send the
URL via the existing socket protocol so the primary handles it. The
guard already supports the activation hook — we extend the protocol
to a newline-terminated payload (`url\n`).

### W7. Manual `.exe` addition

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/windows/WindowsBrowserMetadataExtractor.kt` (new)

**Approach**: read the `.exe`'s embedded PE version-info resource.
Java doesn't ship a parser; options:
- Shell out to PowerShell:
  ```
  Get-Item "C:\Path\to.exe" | Select-Object VersionInfo
  ```
- Use a tiny pure-Java PE reader (~100 LoC).
- Punt — manual addition is rarely used; ship without it and only
  enable on Windows after stage 8.

**Recommendation**: punt for now. UI shows "Add browser…" button on
Windows but the extractor returns `Failure`; same UX as a malformed
.app on macOS. Wire in W7 when we have time.

### W8. PlatformFactory wiring

**Files**:
- `shared/src/jvmMain/kotlin/dev/hackathon/linkopener/platform/PlatformFactory.kt`

For each interface where we now have a Windows impl:

```kotlin
fun createBrowserDiscovery(): BrowserDiscovery = when (currentOs) {
    HostOs.MacOs -> MacOsBrowserDiscovery()
    HostOs.Windows -> WindowsBrowserDiscovery()
    HostOs.Linux,
    HostOs.Other -> EmptyBrowserDiscovery(System.getProperty("os.name").orEmpty())
}
```

Same dispatch update for `LinkLauncher`, `AutoStartManager`,
`BrowserMetadataExtractor` (if W7 ships).

---

## 4. Suggested batching

If we go with the **mid scope** + recommendation 5c:

**Sweep 1 — discovery + launcher (the picker works on Windows):**
- W1 (discovery)
- W2 (launcher)
- W8 (factory wiring for those two)
- Tests: registry-output parser, launcher argv builder.

**Sweep 2 — settings reflect Windows reality:**
- W3 (default-browser read)
- W4 (autostart)
- W8 (factory wiring for autostart)
- Tests: same shape as macOS auto-start tests.

**Sweep 3 — URL receive (deferred / its own iteration):**
- W5 (MSI ProgId — needs WiX-level work)
- W6 (CLI argv handling + single-instance protocol extension)

**Sweep 4 — polish (deferred):**
- W7 (manual .exe addition)
- live `observeIsDefaultBrowser` via JNA registry watch

---

## 5. Verification I can do, and what needs a Windows host

I'm on macOS. Here's what I can and cannot do in this session:

**I can:**
- Write all the Windows-targeted Kotlin code (it's plain JVM).
- Make the codebase compile end-to-end.
- Add unit tests against captured `reg.exe` text fixtures, fake
  process factories, in-memory parsers.
- Run `./gradlew build` and have all 313+ existing tests stay green.
- `Assume.assumeTrue("requires Windows host", isWindows)` style smoke
  tests get skipped on the macOS host (mirroring the existing macOS
  smoke-test pattern from `MacOsBrowserDiscoverySmokeTest`).

**I cannot:**
- Run the app on Windows.
- Verify `reg.exe` output format matches my parser fixtures.
- Verify ProgId registration works.
- Verify Windows actually routes HTTPS URLs to us after registration.
- Test auto-start across reboot.

**User runs**: end-to-end smoke on a Windows machine after each
sweep, reports any divergence. I write the code; user is the test
host.

---

## 6. Acceptance criteria

For each sweep:

- [ ] `./gradlew build` green on the macOS dev host (the existing
      tests + new Windows-targeted unit tests).
- [ ] No macOS regression: existing macOS coverage stays at the same
      Kover line-/method-/class- numbers.
- [ ] New Windows code under `platform.windows.**` excluded from
      Kover's smoke-test category if it shells out, included
      otherwise (mirror what the macOS classes already do — `Plutil-
      Runner` / `MacOsAutoStartManager` are excluded).
- [ ] Each new file has a class-level KDoc explaining the registry
      keys / commands it touches, the same way the macOS classes do.
- [ ] Behavior gaps requiring a Windows host are flagged in
      `CLAUDE.md` so a future contributor understands what's
      verified vs unverified.

---

## 7. Decision points awaiting input

1. **Strategy A or B?** Default: A (reg.exe).
2. **Sweep 1 only, or Sweep 1 + 2?** Default: 1 + 2 — gives the user
   useful Windows behavior and stops at the place that actually
   needs WiX work.
3. **Defer W5/W6/W7?** Default: yes (their own iteration).
4. **Bump version after each sweep?** Default: yes (`1.0.7 → 1.0.8`
   for Sweep 1, `1.0.9` for Sweep 2). Same as round-1/round-2
   refactor sweeps.
