# Refactoring inspection — plan.md

Status: **landed** (single squashed commit on the refactoring branch).

Items shipped in this sweep:
- ✅ #1 Split `SettingsScreen.kt` (1,406 → 168 lines + 13 focused files)
- ✅ #3 Generic JSON helpers in `SettingsRepositoryImpl`
- ✅ #4 Mutex around all repo writes
- ✅ #6 Shared `surfaceContainer*` + `BrowserAvatar` between Settings & picker
- ✅ #7 `mutateRules` helper collapses 5 rule mutators
- ✅ #9 `DebugFlags` single source of truth
- ✅ #12 Drop `AppLanguage` FQN in `AppContainer`
- ✅ #13 Re-indent `CompositionLocalProvider` body (bundled into #1)
- ✅ #14 `useLocaleNonce()` discoverable helper
- ✅ #15 Split `MacOsBrowserDiscovery.discover` into named helpers
- ✅ #16 Drop stale `// region` markers (bundled into #1)

Items deferred:
- ⏭ #2 Use-case explosion — needs explicit OK on test-signature changes
- ⏭ #5 Wide `SettingsRepository` interface — plan recommended "C for now"
- ⏭ #8 `AppContainer` subgraphs — gated on #2
- ⏭ #10 Dead `surfaceContainerLowest` — fell out naturally during #6
- ⏭ #11 `MacOsLinkLauncher` default factory — recommendation was leave-as-is

Below is the original inspection that drove the work, kept for reference.

---

This is an inventory of refactoring opportunities found by reading every
production `.kt` file in the repo. Each finding lists the **problem**,
**why it matters**, two or more **variants** to fix it (cheapest → most
invasive), and a **recommendation**.

**Hard constraint from the user:** *do not touch tests*. Every variant below
is annotated with whether it preserves test signatures or not. Variants that
would force test changes are flagged 🟠 and need explicit user approval before
we proceed.

**Non-goals:** behaviour changes, new features, dependency upgrades, cosmetic
rewrites without rationale, design-system overhauls. We're only making the
existing code easier to navigate, easier to reason about, and easier to
extend.

---

## 0. Inventory snapshot

Production Kotlin (`*.kt`, no tests, no generated): **5,652 lines across 75 files.**

Top 10 files by size:

| Lines | File                                                                |
| ----: | ------------------------------------------------------------------- |
|  1445 | `shared/.../ui/settings/SettingsScreen.kt`                          |
|   289 | `shared/.../ui/settings/SettingsViewModel.kt`                       |
|   278 | `shared/.../ui/picker/BrowserPickerScreen.kt`                       |
|   238 | `desktopApp/.../app/AppContainer.kt`                                |
|   207 | `shared/.../ui/icons/AppIcons.kt`                                   |
|   190 | `desktopApp/.../app/SingleInstanceGuard.kt`                         |
|   185 | `desktopApp/.../app/tray/TrayHost.kt`                               |
|   158 | `shared/.../data/SettingsRepositoryImpl.kt`                         |
|   157 | `desktopApp/.../app/tray/MacOsAlwaysOnTopOverFullScreen.kt`         |
|   144 | `shared/.../ui/theme/LinkOpenerColors.kt`                           |

Use cases in `shared/.../domain/usecase/`: **16** files, of which **14 are
single-line pass-throughs** and **2 carry real logic** (`AddManualBrowserUseCase`,
`DiscoverBrowsersUseCase`).

`SettingsRepository` interface: **10 methods**, **1 production impl**, **5+
test fakes** scattered across test files (every fake re-implements all 10
methods, mostly with `error("not used")`).

---

## 1. `SettingsScreen.kt` is a 1,445-line god-file

### Problem

One Kotlin source mixes ~30 composables across **9 unrelated UI areas**:
top-bar, banner, sidebar, section scaffolding, default-browser pane,
appearance/language/system panes, exclusions list (with notice banner +
loading/empty/error states + browser row + search), rules list (with rule
row + browser dropdown), and shared widgets (icon box, dropdown, surface
helpers).

The author already left `// region` markers for every block — they're a
roadmap pleading for a split.

### Why it matters

- Navigation cost: every change to a tiny composable means scrolling
  through hundreds of lines of unrelated UI.
- Recompile cost: a one-line edit recompiles the whole file (Kotlin's
  per-file granularity).
- Review cost: PR diffs in this file are nearly unreadable in any UI.
- Future moves to compose-ui-test (mentioned in `CLAUDE.md`) become much
  cheaper if each section is independently importable.

### Variants

**A. Split along the existing region markers (recommended).**
Create one file per region:
- `ui/settings/SettingsScreen.kt` (entry point + nav state + the `when` switch — ~80 lines)
- `ui/settings/components/TopAppBar.kt`
- `ui/settings/components/NotDefaultBanner.kt`
- `ui/settings/components/Sidebar.kt`
- `ui/settings/components/SectionScaffold.kt` (`SectionPane` + `SectionCard`)
- `ui/settings/sections/DefaultBrowserSection.kt`
- `ui/settings/sections/AppearanceSection.kt` + `LanguageSection.kt` + `SystemSection.kt` (could stay in one `SimpleSections.kt` since each is ~25 lines)
- `ui/settings/sections/ExclusionsSection.kt` (with its `BrowserList`/`BrowserRow`/`ManualAddNoticeBanner`/`Loading`/`Empty`/`Error` helpers)
- `ui/settings/sections/RulesSection.kt` (with `RuleRow` + `BrowserDropdown`)
- `ui/settings/components/SharedWidgets.kt` (`BrowserIconBox`, `SearchField`, `EnumDropdown`, `surfaceContainerLow`, `surfaceContainerLowest`)

All composables become `internal` so test files in
`ui.settings.*` packages can still reach them; private→internal is a
visibility *widening*, so every existing reference keeps compiling. **No
test signatures change.** ✅

**B. Smaller split: 3 files.**
Top file (entry point + scaffolding + simple sections), one file for
exclusions, one file for rules. Cheapest move; reduces SettingsScreen.kt
to ~600 lines without finer separation. Still test-safe. ✅

**C. Leave it alone.** Kotlin compiles fast enough; risk-free.

### Recommendation

**A**, in one PR. The split is mechanical (cut + paste + add private→internal
on the moved fns). Tests don't import any private composables, so this is
risk-free.

---

## 2. Use-case explosion: 14 of 16 are pass-through one-liners

### Problem

The `domain/usecase/` package has 16 classes. Most look like:

```kotlin
class UpdateThemeUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(theme: AppTheme) = repository.updateTheme(theme)
}
```

Pass-through use cases (no logic): `UpdateThemeUseCase`, `UpdateLanguageUseCase`,
`SetAutoStartUseCase`, `SetBrowserExcludedUseCase`, `SetBrowserOrderUseCase`,
`RemoveManualBrowserUseCase`, `SetRulesUseCase`, `SetShowBrowserProfilesUseCase`,
`GetSettingsFlowUseCase`, `GetIsDefaultBrowserUseCase`,
`OpenDefaultBrowserSettingsUseCase`, `GetCanOpenSystemSettingsUseCase`,
`ObserveIsDefaultBrowserUseCase`, `GetAppInfoUseCase`.

Use cases with real logic: `AddManualBrowserUseCase` (validation, dedupe,
self-check) and `DiscoverBrowsersUseCase` (selfBundleId filter).

### Why it matters

- Every new repo method costs a use-case file + a wiring line in `AppContainer`.
- `SettingsViewModel` constructor takes **14 dependencies**, mostly use cases.
- `AppContainer` has 16 use-case `val`s; readers must scan past them to find anything.
- Pass-through use cases hide nothing — the VM could call the repo directly
  with the same effect.
- The orthodox justification ("use cases let us swap repos") is moot: there's
  one repo per platform and no plans to swap.

### Variants

**A. Keep the pass-throughs as-is.** No change. Accept the ceremony as the
price of consistency.

**B. Inline the pass-throughs into `SettingsViewModel`** 🟠. The VM gets
`settingsRepository: SettingsRepository` directly; `setAutoStart(true)`
becomes `settingsRepository.setAutoStart(true)`. Keep the two use cases
that have logic (`AddManualBrowserUseCase`, `DiscoverBrowsersUseCase`).
- VM constructor shrinks from 14 deps → ~5.
- AppContainer drops 12 declarations.
- **Test impact:** `SettingsViewModelTest` currently injects use-case
  instances — those constructor args change. Flagged 🟠 because user
  said no tests touched. We'd need either: keep the use cases as thin
  facades for tests only (defeats the point), or get explicit user
  permission to update tests.

**C. Replace pass-throughs with a single `SettingsActions` interface**
that VM takes; the impl just delegates to the repo. Doesn't reduce file
count, just moves code. Worse than A and B.

**D. Generate the pass-throughs.** KSP / kapt could produce them.
Over-engineered for 14 trivial classes.

### Recommendation

Discuss before acting. Option **B** is the right Kotlin move per the project's
own "no abstractions beyond what the task requires" principle. But it conflicts
with the no-test-touch rule. Two viable paths:
- **B-soft:** keep pass-throughs *for now* but freeze new ones — when a new
  repo method lands, the VM calls the repo directly. Stops the bleeding,
  doesn't disturb tests.
- **B-full:** ask the user to relax the no-test rule for this one item; do
  the full inline.

---

## 3. `SettingsRepositoryImpl` encode/decode boilerplate (~70 lines)

### Problem

Eight methods of nearly-identical shape:

```kotlin
private fun encodeIds(ids: Set<BrowserId>): String =
    json.encodeToString(SetSerializer(String.serializer()), ids.map { it.value }.toSet())

private fun decodeIds(raw: String?): Set<BrowserId> {
    if (raw.isNullOrEmpty()) return emptySet()
    return runCatching {
        json.decodeFromString(SetSerializer(String.serializer()), raw).map(::BrowserId).toSet()
    }.getOrDefault(emptySet())
}
```

Three more pairs (`encodeOrder`/`decodeOrder`, `encodeManual`/`decodeManual`,
`encodeRules`/`decodeRules`) follow the exact same template, differing only
in the serializer.

### Why it matters

Adding a new `List<X>`-shaped setting today means writing two new methods,
two `KEY_*` constants, plus the public setter and a load() line. The
boilerplate grows linearly with persistent fields.

### Variants

**A. Extract a single generic helper.**
```kotlin
private inline fun <reified T> readJson(key: String, default: T, serializer: KSerializer<T>): T {
    val raw = store.getStringOrNull(key) ?: return default
    return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(default)
}
private fun <T> writeJson(key: String, value: T, serializer: KSerializer<T>) {
    store.putString(key, json.encodeToString(serializer, value))
}
```
Each setter becomes a one-liner. **No test signatures change** (these are
private helpers; tests exercise the public interface). ✅

**B. Add a `JsonStore` wrapper class.** `JsonStore(store, json).readList(key)` /
`writeList(key, value)`. More isolated, more typing. Same test impact.

**C. Migrate to `multiplatform-settings`'s `SerializableSettings` /
`@Serializable` typed accessor.** Bigger change; pulls in `kotlinx-serialization`
deeper into the settings layer. Probably overkill.

### Recommendation

**A**. Pure mechanical refactor, drops ~40 lines, leaves all tests green.

---

## 4. `SettingsRepositoryImpl` read-modify-write races

### Problem

Several setters do read-then-write on `_settings.value` without a mutex:

```kotlin
override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) {
    val current = _settings.value.excludedBrowserIds
    val updated = if (excluded) current + id else current - id
    if (updated == current) return
    store.putString(KEY_EXCLUSIONS, encodeIds(updated))
    _settings.update { it.copy(excludedBrowserIds = updated) }
}
```

If two coroutines call `setBrowserExcluded` concurrently with different
ids, the second can overwrite the first (the `current` snapshot stales
before `update`).

### Why it matters

In practice, callbacks fire on the Main dispatcher sequentially, so
this is **probably** safe today. But:
- It's a footgun if anyone ever calls these from a different scope.
- `BrowserRepositoryImpl` already has a `Mutex` for its read-then-write —
  inconsistent with this layer.
- `MutableStateFlow.update` is the obvious idiomatic fix — it's already
  imported here, just used inconsistently.

### Variants

**A. Use `_settings.update { ... }` for all read-modify-write paths.** The
`update` lambda is run under StateFlow's atomic CAS, so concurrent updates
serialize. Encoding for `store.putString` happens *after* we know the new
value — pull the encode inside the update? No, putString must run only
once per accepted update.

Sketch:
```kotlin
override suspend fun setBrowserExcluded(id: BrowserId, excluded: Boolean) {
    var encoded: String? = null
    _settings.update { current ->
        val ids = if (excluded) current.excludedBrowserIds + id else current.excludedBrowserIds - id
        if (ids == current.excludedBrowserIds) return@update current
        encoded = encodeIds(ids)
        current.copy(excludedBrowserIds = ids)
    }
    encoded?.let { store.putString(KEY_EXCLUSIONS, it) }
}
```

Caveat: `update`'s lambda can re-run on contention, so encoding inside it
would do work twice. The pattern above (capture `encoded` and write after)
keeps store I/O outside the CAS. Tests don't observe internal sequencing,
so they stay green. ✅

**B. Add a single `Mutex` in the repo and `withLock` every setter.** Easier
to read, slightly more contention. ✅ test-safe.

**C. Leave as-is.** Document the "Main dispatcher only" assumption.

### Recommendation

**B** — one mutex, one lock per setter. Simpler than reasoning about CAS
re-runs in every method.

---

## 5. `SettingsRepository` interface is wide; test fakes are painful

### Problem

10 methods × 5 test fakes = 50 stub implementations. Search the test
sources for `error("not used")` and you'll find ~20 of them. When we add
`setShowBrowserProfiles` the existing fakes won't compile until each one
adds the method.

### Why it matters

- Adding a setting today touches 5+ test files for fake updates.
- Friction discourages adding tests that only need part of the interface.

### Variants

**A. Provide a `FakeSettingsRepository` base class** in `commonTest` that
no-ops (or `error`s) every method, so tests only override what they care
about. 🟠 Touches tests by definition (it's in `commonTest`).

**B. Split `SettingsRepository` into role interfaces** (e.g. `SettingsReader`,
`ThemePreferences`, `BrowserPreferences`, `RulePreferences`). Each VM/use-case
declares the narrow interface it needs; the impl implements all of them.
Test fakes get small. 🟠 Public-API change; every site that takes
`SettingsRepository` would need its dep type narrowed (or kept wide). Tests
declare interface types in their fakes, so they'd need updates.

**C. Leave as-is.** Pay the fake-update tax with each new setting.

### Recommendation

**C for now** — every variant either touches tests or fragments a tightly
related domain. Revisit if the interface keeps growing.

---

## 6. Pass-through duplication between `SettingsScreen.kt` and `BrowserPickerScreen.kt`

### Problem

Both files privately define:
- `surfaceContainerLow()` and `surfaceContainerLowest()` — **identical** bodies, wired through `LocalIsDarkMode`.
- `BrowserIconBox` (settings, line 1322) / `IconBox` (picker, line 204) — same shape, slightly different background alpha + an added border in the picker version.
- A "list a Browser" row composable (`BrowserRow` settings line 1008 vs picker line 173) — different actions, but same icon-box/title/subtitle layout primitive.

### Why it matters

If the design system tweaks the surface tones or icon-box style, we touch
the same change twice.

### Variants

**A. Extract `surfaceContainerLow/Lowest` into the theme module** as
top-level `@Composable fun`s alongside `LocalIsDarkMode`. Both screens
import them. Pure deduplication. ✅ test-safe.

**B. Also extract a shared `BrowserAvatar(initial)` composable** (the
common icon-box). Both files use it. ✅ test-safe.

**C. Extract a `BrowserRowBase` slot composable** that takes the common
layout and a `trailing: @Composable RowScope.() -> Unit`. The settings
row's actions and the picker row's plain click are the trailing diff.
Larger surface; risk of over-abstraction. Skip unless we have a third
caller.

### Recommendation

**A + B**. Skip C. Trivial wins, test-safe.

---

## 7. `SettingsViewModel.onAddRule/onRemoveRule/onMoveRule/...` repetition

### Problem

Five rule-mutation methods all read `settings.value.rules`, validate index,
mutate, and call `setRules(newList)`:

```kotlin
fun onUpdateRulePattern(index: Int, pattern: String) {
    val current = settings.value.rules
    if (index !in current.indices) return
    if (current[index].pattern == pattern) return
    scope.launch { setRules(current.mapIndexed { i, r -> if (i == index) r.copy(pattern = pattern) else r }) }
}
```

### Why it matters

The pattern is fragile: every new mutation copies the index check, the
launch, the read of `settings.value.rules`. Easy to forget guards.

### Variants

**A. Inline a private helper `mutateRules(transform: (List<UrlRule>) -> List<UrlRule>?)`** —
returns null = no-op. Each public method becomes:

```kotlin
fun onUpdateRulePattern(index: Int, pattern: String) = mutateRules { current ->
    if (index !in current.indices || current[index].pattern == pattern) null
    else current.mapIndexed { i, r -> if (i == index) r.copy(pattern = pattern) else r }
}
```

✅ test-safe — public signatures unchanged, only internal helpers added.

**B. Move rule mutations into a `RulesEditor` class** that the VM owns.
Bigger surface, breaks the "VM is the only state holder" pattern. Skip.

### Recommendation

**A**. Saves ~25 lines, cuts copy-paste.

---

## 8. `AppContainer` is a 219-line flat wiring file

### Problem

`AppContainer` is a hand-rolled DI graph. Every dependency is a `val`,
declared in one long list. After the use-case explosion (item 2) it
holds 16 use-case declarations, 6 platform interfaces, 4 repos, 1 rule
engine, 1 picker coordinator, 1 SharedFlow, 1 nested locale helper.

### Why it matters

- Large flat scope makes ordering bugs subtle (the recent fix needed
  `systemLanguageTag` *before* `applyJvmLocale` in `init`).
- Hard to see where a dependency is consumed.
- VM construction reads all 14 use-case wirings in one block.

### Variants

**A. Group declarations with comments + blank lines.** Cheapest. Already
half-done. Marginal gain.

**B. Extract sub-graphs.** E.g. `PlatformGraph` (auto-start, discovery,
default-browser-service, link launcher, metadata extractor),
`DataGraph` (settings repo, browser repo, app-info repo),
`UseCaseGraph` (all use cases). `AppContainer` becomes a composition root
that wires the three together. 🟠 — this is invasive enough that test
fakes injecting individual use cases might break; needs case-by-case check.

**C. Adopt a real DI framework (Koin / kotlin-inject).** Out of scope for
a refactor. Skip.

**D. After item 2 (collapse use cases), AppContainer shrinks naturally**
to ~120 lines and the problem largely goes away.

### Recommendation

Defer until item 2 is decided. If 2-B-full lands, do **D** (no AppContainer
work). Otherwise revisit with **B**.

---

## 9. `DEBUG_LOGGING` flag is duplicated

### Problem

`AppContainer.kt:217` defines:
```kotlin
val DEBUG_LOGGING: Boolean = System.getProperty("linkopener.debug") == "true"
```

`TrayHost.kt:105` checks the same string inline:
```kotlin
if (System.getProperty("linkopener.debug") == "true") { ... }
```

### Why it matters

If we ever rename the flag or add a second source (env var fallback,
build-time switch), we have to remember to find both sites.

### Variants

**A. Extract a top-level `internal val DebugFlags.enabled: Boolean`**
in a small `app/DebugFlags.kt` file. `AppContainer` and `TrayHost` both
read it. ✅ test-safe.

**B. Pass `debugLogging: Boolean` through `AppContainer` to a new
`debugMenu: Boolean` field that `TrayHost` reads.** More plumbing.

### Recommendation

**A**. ~5 minutes.

---

## 10. `BrowserPickerScreen.kt` has a dead helper

### Problem

```kotlin
@Suppress("unused")
@Composable
private fun surfaceContainerLowest(): androidx.compose.ui.graphics.Color = ...
```

Marked `@Suppress("unused")` — author kept it because the picker might want
the lower-tone surface later. Today it's dead code with a suppression.

### Why it matters

Tiny, but `@Suppress("unused")` attracts attention on grep and signals
"someone intended to use this and forgot." If item 6 lands (extract surface
tones to theme module), this helper goes away on its own.

### Variants

**A. Delete it** (item 6 will reintroduce it as a shared symbol).

**B. Leave it.**

### Recommendation

**A** — but as part of item 6, not standalone.

---

## 11. `MacOsLinkLauncher` default `processFactory` is uncovered

### Problem

```kotlin
class MacOsLinkLauncher(
    private val processFactory: (List<String>) -> Process = { args ->
        ProcessBuilder(args).inheritIO().start()
    },
)
```

The default lambda runs `ProcessBuilder.start()` and is the missing 2.4%
of line coverage (per `CLAUDE.md` § coverage). Tests inject a fake factory
and never exercise the default.

### Why it matters

Cosmetic — coverage tool flags it. Real-world impact: zero (the lambda is
trivial enough that visual review is sufficient).

### Variants

**A. Leave it.** Document in the coverage exclusion list.

**B. Extract the default into a top-level `defaultProcessFactory` fn** so
it's its own "method" — same coverage gap, just relocated.

**C. Add a coverage exclude rule** for this one lambda.

### Recommendation

**A**. Not worth chasing.

---

## 12. Cosmetic: `dev.hackathon.linkopener.core.model.AppLanguage` referenced fully-qualified in `AppContainer.kt`

### Problem

`AppContainer.kt:205`:
```kotlin
private fun applyJvmLocale(language: dev.hackathon.linkopener.core.model.AppLanguage) {
    val target = java.util.Locale.forLanguageTag(resolveLocaleTag(language, systemLanguageTag))
    ...
}
```

The same type is fully imported elsewhere. No reason for the FQN here.

### Why it matters

Minor noise; consistency with the rest of the file.

### Variants

**A. Add `import dev.hackathon.linkopener.core.model.AppLanguage` at the
top, drop the FQN.** ✅ test-safe.

### Recommendation

**A**. Bundle into item 1 or item 9 cleanup.

---

## 13. Layout / nesting: `SettingsScreen.kt` indentation reset on lines 161–248

### Problem

```kotlin
CompositionLocalProvider(LocalAppLocale provides settings.language.name) {
Box(modifier = Modifier.fillMaxSize()) {        // ← column 4
Surface(...) { ... }                             // ← column 4
}
}
```

The `Box` and `Surface` keep their original indentation when the author
wrapped them in `CompositionLocalProvider` later. Doesn't break anything;
it's just visually misleading.

### Why it matters

Visual signal of an out-of-band wrap. Future readers spend a few seconds
re-checking the brace structure to confirm it's not broken.

### Variants

**A. Re-indent.** ✅ test-safe.

### Recommendation

**A**. Bundle with item 1 (the file gets carved up anyway).

---

## 14. `LocalAppLocale.current` is a discipline checkpoint

### Problem

The `LocalAppLocale` nonce is read at the top of TopAppBar, NotDefaultBanner,
Sidebar, and three other composables in the settings tree to force them out
of Compose smart-skipping when the locale changes.

The doc on `LocalAppLocale` warns that *every* string-using composable that
takes no settings-derived parameter must call `LocalAppLocale.current` or
its strings won't update. Easy to forget when adding a new section.

### Why it matters

A subtle correctness footgun on every new composable. The bug surface from
forgetting it is "language switch leaves my new section in English".

### Variants

**A. Wrap the call in a tiny helper:** `@Composable fun useLocaleNonce() { LocalAppLocale.current }`.
Doesn't change behavior; just gives the read a discoverable name.

**B. Switch to a `@Stable LocaleHolder` class** that wraps the tag and is
provided as a value class. Reading any of its members invalidates dependents.
More elaborate; same outcome.

**C. Document the rule loudly** in `CLAUDE.md` and call it good. Easy
escape hatch if items 1 / 6 land — every new file moved out of
`SettingsScreen.kt` has a comment header reminding the author.

### Recommendation

**A** + **C**. The helper is pure naming; the doc is the real safety net.

---

## 15. `MacOsBrowserDiscovery.discover()` does five things in one method

### Problem

```kotlin
override suspend fun discover(): List<Browser> = withContext(Dispatchers.IO) {
    val candidates = ...   // (a) scan + canonicalize
    val parents = ...      // (b) parallel-read plists
    parents.flatMap { ... }.sortedBy { ... }   // (c) family detect, (d) profile expand, (e) sort
}
```

It works, but each step is a candidate for its own private helper for
readability — especially `expandWithProfiles`, which is already a helper
but the others aren't.

### Why it matters

`discover()` is the single most-tested method on macOS (smoke tests + unit
tests). Each new platform-specific behavior (e.g. "skip browsers in
`/Volumes`") would touch this body.

### Variants

**A. Extract `findCandidatePaths()`, `readBrowsersInParallel()`** as
private helpers; keep `expandWithProfiles` and `resolveSafely`. `discover()`
becomes a 4-line summary. ✅ test-safe (private helpers).

**B. Leave it.** The function fits on one screen; readers can follow it.

### Recommendation

**A** if item 1 is happening anyway (we'd be in refactor mode). Otherwise
**B**.

---

## 16. Comment-vs-code maintenance: `// region` ... `// endregion` pairs

`SettingsScreen.kt` has 8 region pairs that the IDE folds. They're
load-bearing for the current 1,445-line file but **become noise** the
moment item 1 lands. After the split each region is its own file.

**Action:** drop region markers as part of the per-file split.

---

## 17. Other minor / observational notes (no action proposed)

- **`SingleInstanceGuard.kt` (190 lines)** — JVM-y file with `ServerSocket`
  + thread; not Compose territory; reads cleanly. No refactor pending.
- **`MacOsAlwaysOnTopOverFullScreen.kt` (157 lines)** — JNA bridge into
  AppKit. Self-contained, well-commented. Don't touch.
- **`AppIcons.kt` (207 lines)** — hand-rolled vectors. Splitting per-icon
  files would be churn for no readability win.
- **`LinkOpenerColors.kt` (144 lines)** — design tokens; size is data, not
  logic.
- **`TrayHost.kt` (185 lines)** — borderline, but the single composable
  + two helpers structure is clear; not splitting.

---

## 18. Suggested execution order

If approved, recommended order to keep PRs small and reviewable:

| Step | Item                                                          | Effort | Test impact   |
| ---: | ------------------------------------------------------------- | -----: | ------------- |
|    1 | **Item 9** — extract `DebugFlags`                             | 5 min  | none          |
|    2 | **Item 6** — share `surfaceContainer*` + `BrowserAvatar`      | 30 min | none          |
|    3 | **Item 3** — generic JSON helpers in `SettingsRepositoryImpl` | 30 min | none          |
|    4 | **Item 4** — single `Mutex` for repo writes                   | 20 min | none          |
|    5 | **Item 7** — `mutateRules` helper in VM                       | 20 min | none          |
|    6 | **Item 1** — split `SettingsScreen.kt` per region (+ items 12, 13, 14, 16) | 1–2 h | none |
|    7 | **Item 15** — extract macOS discovery helpers                 | 30 min | none          |
|    8 | **Item 2** — discuss with user: B-soft vs B-full              | TBD    | depends on choice |
|    9 | **Item 8** — revisit AppContainer after #2 lands              | TBD    | depends       |

Items 1–7 are all individually test-safe and individually small. Each
should be a separate commit (per `CLAUDE.md` workflow), all on this
`refactoring` branch. The last two are gated on user input.

---

## 19. Out of scope (not refactoring)

- Replacing the placeholder `Refresh` icon (TODO comment in `AppIcons.kt`).
- Sonoma+ Default-browser settings deep-link TODO in `MacOsDefaultBrowserService`.
- Stage 7 / Stage 8 (Windows / Linux) — feature work, not refactor.
- Mailto / non-http URL scheme support — feature.
- TD-1, TD-4, TD-6, TD-8 from `TECHDEBT.md` — separate followups.

---

## 20. Acceptance criteria for this refactoring

A future "refactoring done" PR is acceptable when:

- [ ] `./gradlew build` is green.
- [ ] `./gradlew :shared:koverHtmlReport` shows no regression in line / branch coverage.
- [ ] No test file is modified (per the user's hard constraint).
- [ ] No production behavior change observable from the Settings UI, the
      picker, or the launcher.
- [ ] `SettingsScreen.kt` is < 200 lines.
- [ ] `SettingsRepositoryImpl.kt` is < 100 lines (after item 3).
- [ ] All new files follow the existing package layout (`ui.settings.sections`,
      `ui.settings.components`, `ui.theme.surface`).
