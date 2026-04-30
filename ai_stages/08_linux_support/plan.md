# Stage 08 — Linux support

Goal: bring Linux from "stubs that no-op" to "all the same flows that
work on macOS, work on Linux Mint Cinnamon" (and any other XDG-compliant
desktop the user may try later — GNOME, KDE, XFCE, MATE).

The roadmap entry from `00_overview.md` lists Linux as the last open
stage; everything else (settings, picker, rules, profiles toggle,
running-state probe, design system, i18n) is already platform-agnostic
and just sees per-platform `…Discovery` / `…LinkLauncher` / etc.
implementations. Stage 08 fills those slots.

## What's already there

- `LinuxBrowserIconLoader` — full freedesktop icon-theme walker; ready
  to consume `.desktop` paths from a real discovery.
- `JvmRunningBrowserProbe` already supports Linux (`ps -ax -o comm=`)
  with exact-match path comparison. No work here.
- `JvmUrlReceiver` — `Desktop.setOpenURIHandler` is mac-only at the JDK
  level; on Linux it silently fails the `isSupported` check. URL
  receive on Linux therefore goes via argv (we get spawned with the URL
  on argv by `xdg-open` -> our `.desktop`'s `Exec=` line, exactly like
  Windows). `Main.kt` already parses `urlFromArgs` and routes via
  `SingleInstanceGuard`, which works on Linux unchanged (fcntl + TCP
  loopback, OS-portable).
- `SingleInstanceGuard` — fcntl + ServerSocket are JVM cross-platform.
  No changes.

## What needs to land

### 1. `LinuxBrowserDiscovery`

Scan XDG application dirs:

- `$XDG_DATA_HOME/applications` (`~/.local/share/applications`)
- each entry of `$XDG_DATA_DIRS/applications` (default
  `/usr/local/share/applications:/usr/share/applications`)
- common Snap / Flatpak overlays (`/var/lib/snapd/desktop/applications`,
  `/var/lib/flatpak/exports/share/applications`,
  `~/.local/share/flatpak/exports/share/applications`)

For each `.desktop` file:

- Skip `Hidden=true` and `NoDisplay=true` entries (freedesktop spec
  treats `Hidden` as "uninstalled at user level" and `NoDisplay` as
  "exists but don't show in app pickers"; for our purposes both mean
  "don't list in the picker").
- Skip if `Type` != `Application`.
- Keep only entries whose `MimeType=` contains
  `x-scheme-handler/http` or `x-scheme-handler/https`. That's the
  freedesktop equivalent of macOS's `CFBundleURLTypes` http/https
  filter — exactly the apps that say "I can open URLs".
- Extract `Name=` (locale-suffix-aware: prefer `Name[ru]` when locale
  is `ru`, fall back to bare `Name`), `Exec=` (kept around for the
  launcher), `Icon=` (consumed by `LinuxBrowserIconLoader`).

`Browser` mapping:

- `applicationPath` — absolute path of the `.desktop` file. Same
  contract as macOS `.app` path / Windows `.exe` path: opaque to the
  rest of the graph; only the per-OS launcher and icon loader read
  inside it. Lines up with what `LinuxBrowserIconLoader` already
  expects.
- `bundleId` — `.desktop` basename without extension
  (`firefox.desktop` -> `firefox`). Stable across upgrades, mirrors
  what `xdg-mime default <id> x-scheme-handler/http` takes. This is
  the Linux analogue of the macOS reverse-DNS bundle id and the
  Windows registry sub-key.
- `displayName` — `Name=` value.
- `version` — null (Stage 08 doesn't parse versions; could add later
  via `<exec> --version`, but every browser has its own format).
- `family` — derived from `bundleId` / `displayName` keywords via
  `detectBrowserFamilyByDisplayName` (the same heuristic Windows uses).
- `profile` — null at this stage. Linux Chromium profile scanning
  (`~/.config/google-chrome/Local State`, etc.) is a follow-up.

Dedupe:

- `distinctBy { it.applicationPath.canonicalPath }` so a symlink and
  its target collapse.
- Order: alphabetical by displayName lowercase, matching mac/Windows.

### 2. `LinuxLinkLauncher`

Three argv shapes, same family-driven dispatch as `MacOsLinkLauncher`:

- **Chromium with profile:** `<binary> --profile-directory=<id> <url>`.
  Same as Windows — Chromium accepts the profile flag on argv and the
  fresh process forwards through Chromium IPC if an instance is
  already running. Profile expansion isn't wired in stage 08, but the
  family/profile branch stays for forward compatibility.
- **Firefox-family:** `<binary> <url>`. Bypasses any potential cold-
  start race the same way mac does — Mozilla's XPCOM remoting handles
  warm/cold uniformly when invoked by binary path. Linux is normally
  not subject to bug 531552 (no Apple Events involved), but the
  shape is identical.
- **Everyone else:** `<binary> <url>`.

To get `<binary>`, parse `Exec=` from the `.desktop` (which is
`applicationPath`) and:
- Split into argv via shell-style tokenization (quoted segments stay
  whole). The freedesktop spec mandates a small grammar; using
  `Runtime.exec`-style splitter (or our own simple split-respecting-
  quotes) is enough for real-world entries.
- Drop placeholder field codes — `%u %U %f %F %i %c %k` — per spec
  table (`%u` is "single URL", `%U` is "list of URLs", etc.). We're
  appending the URL ourselves after stripping these.
- Resolve relative binary names via PATH (`firefox` -> first hit on
  `$PATH`).

### 3. `LinuxDefaultBrowserService`

Read default browser by running `xdg-settings check default-web-browser
<our.desktop>` — Chrome's exact probe (see Chromium's
`shell_integration_linux.cc`). xdg-settings checks all of
`x-scheme-handler/http`, `x-scheme-handler/https` AND `text/html` and
prints "yes"/"no", which is more correct than `get` because partial
binding (e.g. only http) doesn't fool us. Falls back to reading
`~/.config/mimeapps.list` `[Default Applications]`
`x-scheme-handler/http=` if `xdg-settings` is missing.

`observeIsDefaultBrowser()` — watch the parent dir of
`~/.config/mimeapps.list` for ENTRY_MODIFY/CREATE/DELETE events on
that filename, same WatchService pattern macOS uses for the
LaunchServices plist. `distinctUntilChanged()` after every emission.

`canOpenSystemSettings` + `openSystemSettings()` — DE-aware dispatch:

| `XDG_CURRENT_DESKTOP` contains | Command |
| --- | --- |
| `Cinnamon` | `cinnamon-settings default` |
| `GNOME` / `Unity` | `gnome-control-center default-applications` |
| `KDE` | `kcmshell6 kcm_componentchooser` (fall back to `kcmshell5`) |
| `XFCE` | `exo-preferred-applications` |
| `MATE` | `mate-default-applications-properties` |
| anything else | none — `canOpenSystemSettings = false` |

Each entry verifies the command exists in `$PATH` before claiming
`canOpenSystemSettings = true`. The Settings UI's "Open settings"
button is conditional on that flag, so an unsupported DE just shows
the text-only banner.

### 4. `LinuxAutoStartManager`

Drop / remove `~/.config/autostart/link-opener.desktop`. The XDG
autostart spec says any `.desktop` file in
`$XDG_CONFIG_HOME/autostart` (or `/etc/xdg/autostart` for system
defaults) gets launched on session start when its `Hidden` /
`X-GNOME-Autostart-enabled` keys say so. We use the user-scope
location so no elevation is needed.

Generated file:

```
[Desktop Entry]
Type=Application
Name=Link Opener
Exec=<launch tokens>
X-GNOME-Autostart-enabled=true
Hidden=false
NoDisplay=false
```

`<launch tokens>` reuses the same `WindowsLaunchCommand`-shaped
detection: pull from `ProcessHandle.current()` and produce either
`<exe>` (jpackage / DEB install) or `<java> -jar <jar>` (fat JAR
shape). Pulled into a small Linux-side helper
(`LinuxLaunchCommand`) with the same idea.

### 5. `LinuxBrowserMetadataExtractor`

Manual-add flow expects a path. On Linux the user picks a `.desktop`
file. Extractor:

- Refuse if not a regular file or extension != `.desktop`.
- Parse `Name`, `Exec`, `MimeType` via the same parser used by
  discovery.
- Refuse if `Type != Application` or `MimeType` doesn't include http/
  https — same gate the auto-discovery uses.
- Build a `Browser` with `applicationPath = canonical .desktop path`.

Reuses the discovery's parser; both live under
`platform/linux/DesktopEntry.kt`.

### 6. Register-as-browser support

Linux has no "Info.plist installed at .app install time" equivalent
for fat-JAR distribution. We need to install our own `.desktop` file
at runtime so we appear in System Settings -> Default Applications
and `xdg-mime` can find us when the user picks us.

Modeled on Chrome's installer behavior (verified against the
Chromium source `chrome/browser/shell_integration_linux.cc`): drop a
`.desktop` file under `$XDG_DATA_HOME/applications`, refresh the
freedesktop cache, and **stop there**. Chrome notably does not
silently rebind `x-scheme-handler/http` to itself at every launch —
the rebind is a user-driven action through System Settings (which
internally uses `xdg-settings set default-web-browser …`). Doing it
silently every startup would steal a default the user might not
have chosen. We do the same.

`LinuxHandlerRegistration.register()`:

- Writes `~/.local/share/applications/link-opener.desktop` with
  `MimeType=x-scheme-handler/http;x-scheme-handler/https;text/html;`
  (`text/html` is required for `xdg-settings check` to register us
  as the default web browser — see § 3), `Exec=<launch tokens> %u`,
  `Name=Link Opener`, `Categories=Network;WebBrowser;`.
- Runs `update-desktop-database ~/.local/share/applications` if that
  binary exists in `$PATH` so the cache picks up the new entry
  immediately.

Registration is invoked at app start on Linux (mirrors the Windows
`WindowsHandlerRegistration().register()` call from `Main.kt`),
idempotent: rewriting the same content every startup is cheap and
self-heals partial state.

Bundled in a new class `LinuxHandlerRegistration` alongside
`WindowsHandlerRegistration` (the Windows class is structurally
similar but does much more registry plumbing — Linux is conceptually
simpler thanks to xdg). Filtered out of the picker the same way
Windows is — `selfBundleIdForDiscovery` knows the `HostOs.Linux` arm
returns `link-opener`.

### 7. PlatformFactory wiring

Replace the four `Linux ->` arms that point at `EmptyBrowserDiscovery`
/ `PrintingLinkLauncher` / `LinuxDefaultBrowserService` (the no-op) /
`UnsupportedManualBrowserExtractor` with the new classes. The
`HostOs.Linux` arm of `createDefaultBrowserService` keeps its existing
class name but now actually does work.

### 8. AppContainer wiring

- `selfBundleIdForDiscovery` learns a `HostOs.Linux ->
  LinuxHandlerRegistration.OWN_DESKTOP_ID` arm.
- A new `if (currentOs == HostOs.Linux) runBlocking {
  LinuxHandlerRegistration().register() }` block in `Main.kt`,
  symmetric with the existing Windows block.

## Tests

All under `:shared` jvmTest, gated with `Assume.assumeTrue(isLinux)`
on the few that touch real `xdg-mime` etc. The parser, argv builder,
and registration body-string builder are pure and testable on any
host (mac/Windows CI runners).

- `LinuxBrowserDiscoveryTest` — temp dir with hand-written
  `.desktop` fixtures (browser, non-browser, hidden, non-application
  type), assert filtering + ordering + dedupe.
- `LinuxLinkLauncherTest` — fake process factory, verify argv shapes
  for Chromium-with-profile / Firefox / generic cases. Also covers
  `Exec=` placeholder stripping.
- `LinuxDefaultBrowserServiceTest` — fake process runner returning
  `linkopener.desktop` -> isDefault = true; symmetric for Firefox.
- `LinuxAutoStartManagerTest` — round-trip enable/disable, plist file
  shape.
- `LinuxBrowserMetadataExtractorTest` — happy path + the three
  refusal cases (wrong extension, Type=Service, MimeType missing
  http).
- `LinuxHandlerRegistrationTest` — write to a tmp XDG home, assert
  resulting `.desktop` content + the xdg-mime command-list invocation
  (via a fake runner).
- `DesktopEntryTest` — parse-only, covers locale-suffix lookup,
  Exec= field-code stripping, multi-section files (Desktop Action),
  comments, blank lines.

## Out of scope for stage 08

- Linux Chromium profile expansion. Stage 08.1 if requested — the
  paths are similar to mac (`~/.config/google-chrome/Local State`)
  but the dance is non-trivial. Single-row Chrome already lands fine.
- Snap / Flatpak portal-based URL routing. Snap browsers' `.desktop`
  files do appear in the standard XDG dirs and our launcher invokes
  them via `Exec=` which dispatches into `snap run …` correctly, so
  this works out of the box. Native portal API is a future polish.
- DEB packaging. Compose's `nativeDistributions` already supports
  DEB (with `MimeType` declared in `linuxNativeDistributions`); we
  rely on the fat-JAR + runtime self-registration path here. DEB is
  a follow-up if Mint users complain about manual install.
- Linux notarization / signing. N/A — there's no Linux equivalent.

## Manual test plan (Linux Mint Cinnamon)

Prerequisites the user needs on a fresh Mint:

- `sudo apt install openjdk-17-jre` (Mint preinstalls 11; we need 17+
  for the Compose Multiplatform 1.11 toolchain).
- `xdg-utils`, `desktop-file-utils` — preinstalled.
- Cinnamon supports legacy tray icons natively, so AWT's SystemTray
  works without `snixembed`.

Steps:

1. Drop `link-opener-1.0.X-linux-x64.jar` somewhere (e.g.
   `~/Apps/link-opener.jar`).
2. `java -jar ~/Apps/link-opener.jar` — tray icon should appear in
   the panel; right-click -> Settings opens the window.
3. Settings -> Browsers — should list Firefox / Chromium / etc.
   discovered from `/usr/share/applications`.
4. Settings -> banner says "Link Opener is not the default browser",
   click "Open System Settings" -> Cinnamon's Default Applications
   dialog opens; pick "Link Opener" under Web. Banner flips green.
5. Click any link in another app -> picker pops up; pick a browser ->
   that browser opens the URL.
6. Toggle "Start at login" -> log out / in -> tray icon comes back
   automatically.

If the app fails to start, we want stdout/stderr from
`java -jar … 2>&1 | tee /tmp/lo.log` so the failure mode is
diagnosable (most likely candidates: missing libfreetype / libGL,
Wayland compositor without XWayland for AWT, JDK <17).
