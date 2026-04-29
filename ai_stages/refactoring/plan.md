# Refactoring inspection — round 2

Status: **partially landed**.

- ✅ Sweep 2A (test-safe items, this commit): **R4, R5, R7, R8**.
- 🟠 Pulled out of sweep 2A as *not* test-safe: **R1, R9**. Now alongside
  the originally-deferred items.
- ⏭ Deferred (need explicit OK on test changes or further design discussion):
  **R1, R2, R3, R6, R9, R10, R11**.

This supersedes the round-1 plan. Round 1 landed as commit `69ac921` on main
(Settings UI split, design helpers shared, repo polish, etc.) — every item
marked "✅ landed" in the previous version is in main now. Round 2 is a fresh
top-to-bottom inspection looking for *what still doesn't sit right* once the
easy wins are gone.

**Hard constraint reaffirmed:** *do not change tests* unless the user
explicitly opts in per item. Each variant below is annotated with whether it
preserves test signatures (✅) or would force test updates (🟠).

**Non-goals:** features, dep upgrades, behaviour changes, Compose perf
micro-optimisations, design-system changes.

---

## 0. Inventory snapshot (post round 1)

Production Kotlin: **6,042 lines across 90 files** (was 5,652 / 75 — the +600
line / +15 file delta comes mostly from splitting SettingsScreen.kt into
focused files).

Top 10 by size:

| Lines | File |
| ----: | --- |
|   370 | `shared/.../ui/settings/sections/ExclusionsSection.kt` |
|   287 | `shared/.../ui/settings/SettingsViewModel.kt` |
|   276 | `shared/.../ui/settings/sections/RulesSection.kt` |
|   240 | `shared/.../ui/picker/BrowserPickerScreen.kt` |
|   233 | `desktopApp/.../app/AppContainer.kt` |
|   207 | `shared/.../ui/icons/AppIcons.kt` |
|   190 | `desktopApp/.../app/SingleInstanceGuard.kt` |
|   185 | `desktopApp/.../app/tray/TrayHost.kt` |
|   168 | `shared/.../ui/settings/SettingsScreen.kt` |
|   157 | `shared/.../ui/settings/components/Sidebar.kt` |

No file in production code is now over 400 lines — UI surface is healthy.
The biggest remaining outliers are `ExclusionsSection.kt` (one cohesive section,
acceptable) and `SettingsViewModel.kt` (still bloated by use-case ceremony).

---

## 1. What's already clean — leave it alone

These pieces survived inspection without remarks. Future "improvements" to
them risk regressing actually-good architecture.

- **`RuleEngine`** — well-isolated pure-domain class with documented seam
  points (`extractTarget`, `findFirstApplicable`, exclusion / missing-browser
  branches). Easy to test, easy to evolve. Don't touch.
- **`HostGlobMatcher`** — single-purpose object, tight regex compilation
  with a documented cookie-domain shortcut. Comprehensive test coverage.
- **`SettingsRepositoryImpl`** (post round 1) — `Mutex`-serialized writes,
  generic `readJson`/`writeJson`, single companion of cached serializers.
  Good shape.
- **`MacOsBrowserDiscovery.discover()`** (post round 1) — four-line summary
  delegating to two named helpers. Clean.
- **`SingleInstanceGuard.acquireOrSignal`** — careful resource ordering
  (lock then socket then port file; reverse-unwind on each failure).
  Actually a model of how to write JVM-y resource-acquisition code. Don't
  "simplify" the early-return chain; it's already as flat as it gets.
- **Domain models in `core/model/`** — small, immutable, `@Serializable`
  where needed. `BrowserId` as a `@JvmInline value class` is the right call.
- **The "single point of decision" pattern** documented in stage plans
  (`RuleEngine` for rule policy, `BrowserRepositoryImpl.collapseProfilesIfDisabled`
  for profile collapse, `applyJvmLocale`/`resolveLocaleTag` for locale
  resolution). Keep this convention for new features.

---

## 2. Items from round 1 still open

These were deferred, not abandoned. Their analysis hasn't changed; bringing
them up here so the new plan is the single point of reference.

| Old # | Item | Status today |
| --- | --- | --- |
| #2 | Use-case collapse (14 of 16 are pass-through one-liners) | 🟠 needs test edits — see new finding **R3** below for an updated take |
| #5 | Wide `SettingsRepository` interface (10 methods, 5+ test fakes) | Still "C for now" per round 1; revisit if interface keeps growing |
| #8 | `AppContainer` flat 200-line wiring | Gated on R3 — solved by it; revisit afterward |

---

## 3. New findings (round 2)

Numbered **R1, R2, …** to distinguish from round-1 numbering and to keep
cross-references unambiguous.

### R1. `SettingsViewModel` doesn't own its `CoroutineScope`

**Files**: `shared/.../ui/settings/SettingsViewModel.kt:49`,
`desktopApp/.../app/AppContainer.kt:51` + `:198`.

**Problem**: VM takes `scope: CoroutineScope` as a constructor dependency.
AppContainer passes its long-lived `Main`-bound `SupervisorJob` scope. The VM
launches three persistent collectors at init (`observeIsDefaultBrowser`,
initial discovery, initial default-browser read) and never cancels them.

In practice today this is benign — `newSettingsViewModel()` is called exactly
once via `remember(container) { ... }` in `TrayHost`. But:
- If the user closes Settings, the VM's collectors keep running forever
  (Main thread holds them until process exit).
- If `newSettingsViewModel()` were ever called twice, the first VM's coroutines
  would leak and continue writing to its dropped `_browsers`/`_isDefaultBrowser`
  state flows.
- It violates the "VM owns its lifecycle" contract that every other VM I've
  ever seen follows.

**Why it matters**: Architectural cleanliness aside, this is a footgun
waiting for someone to add a "reset settings" feature that recreates the VM.

**Variants**:
- **A. VM creates its own `CoroutineScope`** from `SupervisorJob() + dispatcher`
  (dispatcher injected for tests). Adds a public `dispose()` method. Keep
  the `scope` constructor parameter as `dispatcher: CoroutineDispatcher` for
  test injection. ✅ test-safe — tests pass `Dispatchers.Unconfined` (or use
  the existing `runTest` scheduler) and call `dispose()` in teardown.
- **B. VM accepts `parent: Job?` and creates a child** so cancellation
  cascades from a parent. More plumbing; less independent.
- **C. Leave it.** Acceptable today. Document the assumption in a class
  KDoc.

**Recommendation**: **A**. Small change, real architectural win.

### R2. `applyLocale` callback in commonMain VM is a JVM-Locale leak

**Files**: `shared/.../ui/settings/SettingsViewModel.kt:54, :106-115`,
`desktopApp/.../app/AppContainer.kt:199, :206-211`.

**Problem**: `SettingsViewModel.onLanguageSelected` takes
`applyLocale: (AppLanguage) -> Unit` and calls it *synchronously on the click
thread before the suspending update fires*, to win a race against the Window
subcomposition. The VM lives in `commonMain` but the only sensible
implementation of `applyLocale` is `java.util.Locale.setDefault` — a JVM API.

This means the commonMain VM is shaped around a JVM concern. The default
no-op (`{}`) lets tests not care, but the contract leaks.

**Why it matters**: If we ever add a non-JVM target (which the project's KMP
shape suggests we might want to), `AppLanguage` selection is half-broken
without re-implementing the JVM trick on each platform. Today it's also a
puzzle for new readers — *"why does the VM call a callback before launching
the coroutine?"*.

**Variants**:
- **A. Move the locale flip out of the VM entirely.** Create a
  `LocaleApplier` JVM service that observes `settings.language` flow on a
  high-priority dispatcher and calls `setDefault` synchronously. Wire it in
  AppContainer. VM no longer takes `applyLocale`. The Compose race is
  re-fought *outside* commonMain. ✅ test-safe — VM constructor loses a
  parameter (default makes existing tests still pass).
- **B. Make `applyLocale` an injected `LocaleApplier` interface** (still
  passed to VM) so commonMain has the abstraction even if the JVM is the
  only impl. Cleaner naming than a raw lambda. ✅ test-safe.
- **C. Leave it.** Add a KDoc explaining why the callback exists.

**Recommendation**: **B** as the smallest cleanup that makes the contract
explicit. **A** if/when a second platform actually arrives.

### R3. Use-case collapse — updated take after round 1

**Files**: `shared/.../domain/usecase/*.kt` (16 files, 14 pass-throughs).

**Problem (unchanged from round 1 #2)**: 14 of 16 use cases are one-line
delegations. `UpdateThemeUseCase`, `SetAutoStartUseCase`,
`SetBrowserExcludedUseCase`, etc. are all variations on:

```kotlin
class XUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(...) = repository.someMethod(...)
}
```

VM constructor takes 14 dependencies; AppContainer holds 16 use-case `val`s.

**What's changed since round 1**: Round 1 did NOT touch these. The other
refactors (`mutateRules`, `Mutex`, etc.) made the pass-through ceremony
*more* visible by contrast — clean code now everywhere except this one
ceremony layer.

**Variants** (same as round 1 — restating for the record):
- **A. Keep as-is.** No change.
- **B-soft**: freeze new pass-throughs but keep existing ones. ✅ test-safe.
  Doesn't help current readability but stops the bleeding.
- **B-full**: inline pass-throughs into VM. VM takes
  `settingsRepository: SettingsRepository` directly and calls
  `settingsRepository.setRules(...)`. Keep the two real use cases
  (`AddManualBrowserUseCase`, `DiscoverBrowsersUseCase`). VM constructor
  drops from 14 deps → 5; AppContainer drops 12 declarations.
  🟠 — VM constructor signature changes; tests will not compile until
  updated.

**Recommendation**: This remains the single biggest unaddressed item. Two
options for moving forward:

1. **Bite the bullet on tests for this one item** — explicitly authorise B-full
   as a one-time exception.
2. **Stay with A** and accept the ceremony as the price of test isolation.

I genuinely think the ceremony is the worse trade — but it's the user's call.
Round 1 documented this; round 2 doesn't have new information to flip the
recommendation, just renewed conviction.

### R4. `CancellationException` rethrow boilerplate (3+ sites)

**Files**: `shared/.../ui/settings/SettingsViewModel.kt:91-99, 248-257, 270-278`,
`shared/.../ui/picker/PickerCoordinator.kt:50-56`,
`desktopApp/.../app/AppContainer.kt:172-178`.

**Problem**: Five sites repeat the same shape:

```kotlin
try {
    block()
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    handleError(t)
}
```

Forgetting the `CancellationException` rethrow swallows cancellation and is a
classic Kotlin coroutines footgun.

**Variants**:
- **A. Extract `runCatchingNonCancellation { … }` extension** in a
  small commonMain `coroutines/Cancellation.kt`. Each call site becomes:
  ```kotlin
  runCatchingNonCancellation { block() }.onFailure { handleError(it) }
  ```
  Or a `tryCancellable { ... } onFailure { ... }` style helper.
  ✅ test-safe (helper is internal).
- **B. Use `kotlinx.coroutines.suspendCancellableCoroutine` /
  `runCatching`-with-rethrow**. Cleaner Kotlin style but doesn't dedupe.

**Recommendation**: **A**. Each site reads as the helper's name making the
contract explicit, instead of three lines of ceremony.

### R5. `PickerCoordinator` silently swallows discovery errors

**Files**: `shared/.../ui/picker/PickerCoordinator.kt:50-56`.

**Problem**: When `discoverBrowsers()` throws, the picker shows
`Showing(emptyList())` with no log. The user sees an empty popup with no
clue why; the dev sees nothing in stderr. By contrast,
`AppContainer.kt:177` *does* log to stderr on the same kind of failure
("Browser discovery failed: …").

**Why it matters**: This is the single hottest path in the app — every
clicked link goes through here. A silent failure is a debugging hellscape.

**Variants**:
- **A. Add a `log: (String) -> Unit = ::println` ctor parameter** (mirroring
  RuleEngine's existing logging hook); call it from the catch with the
  exception message. Unconditional stderr — failures are rare and worth
  knowing about. ✅ test-safe.
- **B. Add a separate `PickerState.LoadFailed(message)`** state so the UI
  can render a red banner instead of an empty list. Bigger UI change.

**Recommendation**: **A** now (5 minutes), revisit **B** if users actually
hit this.

### R6. Repository setter naming: `update*` vs `set*`

**Files**: `shared/.../domain/repository/SettingsRepository.kt:14-22`.

**Problem**: 9 setters split 2/7 between `updateX` (`updateTheme`,
`updateLanguage`) and `setX` (every other one). Both verbs are doing the
same thing. The split is historical, not principled.

**Why it matters**: Adding a new setting today has no rule to follow. The
inconsistency is also a small barrier to grepping for "all the places we
mutate settings".

**Variants**:
- **A. Rename `updateTheme` → `setTheme`, `updateLanguage` → `setLanguage`**.
  🟠 — tests call `repo.updateTheme(...)`; ~5 test files need updates.
- **B. Rename everything to `update*`** instead. Same test impact.
- **C. Leave it.** Document the convention going forward in CLAUDE.md.

**Recommendation**: **C** unless we're already breaking the no-test rule for
**R3**. If R3 happens, batch the rename into the same PR.

### R7. `ExclusionsSection` re-implements `SectionCard` inline

**Files**: `shared/.../ui/settings/sections/ExclusionsSection.kt:220-240, 241-253`.

**Problem**: The "no browsers match search" empty-state Box and the wrapping
Column for the browser list both hand-roll the rounded-bordered-padded look
that `components/SectionCard.kt` already provides. The empty-state
inline could be `SectionCard { Text(...) }` (six lines instead of twenty).
The list wrapper is *almost* a SectionCard but with `padding(0)` instead of
`padding(16)` so dividers reach the edge — that one's intentional, leave it.

**Variants**:
- **A. Replace empty-state Box with `SectionCard`.** ✅ test-safe.

**Recommendation**: **A**. Trivial.

### R8. `TrayHost.kt` uses fully-qualified types in `TrayHostBody` parameters

**Files**: `desktopApp/.../app/tray/TrayHost.kt:62-64`.

**Problem**:
```kotlin
private fun ApplicationScope.TrayHostBody(
    container: AppContainer,
    settings: dev.hackathon.linkopener.core.model.AppSettings,
    settingsViewModel: dev.hackathon.linkopener.ui.settings.SettingsViewModel,
    ...
)
```
Same FQN smell as round-1 #12 had in `AppContainer.kt`. Inconsistent with
the rest of the file (which has top-level imports for everything else).

**Variants**:
- **A. Add `import dev.hackathon.linkopener.core.model.AppSettings` and
  `import dev.hackathon.linkopener.ui.settings.SettingsViewModel`**. ✅
  test-safe.

**Recommendation**: **A**. Trivial.

### R9. `SingleInstanceGuard.onActivationRequest` is a `@Volatile var`

**Files**: `desktopApp/.../app/SingleInstanceGuard.kt:43-44`,
`desktopApp/.../app/Main.kt:14-15`.

**Problem**:
```kotlin
@Volatile
var onActivationRequest: () -> Unit = {}
```

It's initialized as a no-op then reassigned in `Main.kt` after the guard is
constructed. The guard's listener thread is already running when the no-op
default exists, so any activation pings between construction and
reassignment go to `/dev/null`. In practice the window is microseconds, but
it's a logical race.

Plus: mutable public state is a smell. The contract is "set this once,
forever".

**Variants**:
- **A. Take callback as a constructor parameter to `acquireOrSignal()`**.
  Listener thread isn't started until after construction (it's started in
  the `init` block via `Thread(::runListener).start()`); pass the callback
  in, and start the listener only after we have it. ✅ test-safe (test fakes
  pass a no-op or explicit handler).
- **B. Use an `AtomicReference<() -> Unit>`** so the assignment is publicly
  visible without `@Volatile` semantics. Same "set after construction"
  problem, just dressed up. Don't do this.
- **C. Leave it.** It works. The race window is sub-millisecond.

**Recommendation**: **A**. It's a tiny correctness improvement for the same
LOC.

### R10. `BrowserRepository` is request-response, not reactive

**Files**: `shared/.../domain/repository/BrowserRepository.kt`,
`shared/.../data/BrowserRepositoryImpl.kt`.

**Problem**: BrowserRepository exposes `suspend fun getInstalledBrowsers():
List<Browser>` and `suspend fun refresh()`. When `showBrowserProfiles` flips,
nobody learns about it — `BrowserRepositoryImpl.collapseProfilesIfDisabled`
only runs on the next `getInstalledBrowsers()` call. The VM works around this
by manually calling `loadBrowsers(false)` after the toggle
(`SettingsViewModel.kt:128-131`); the picker dodges it because it always asks
on every URL.

**Why it matters**: It's a "remember to invalidate" footgun. Anyone adding a
new "settings change that affects browser shape" has to remember to also
trigger a refresh. With a `StateFlow`, callers `collectAsState()` and
forget about invalidation entirely.

**Variants**:
- **A. Repository exposes `StateFlow<List<Browser>>`** that internally
  combines discovery output with `settings.flow` for the parts that
  affect shape. Existing `getInstalledBrowsers()` becomes
  `installed.value`. Refresh becomes a method that re-runs discovery
  and feeds the flow.
  🟠 — tests on `BrowserRepositoryImpl` and `AddManualBrowserUseCase`
  read `getInstalledBrowsers()` directly; signature change cascades.
- **B. Leave it.** Document the invalidation-on-toggle responsibility in
  `BrowserRepository`'s KDoc so future authors don't miss it.

**Recommendation**: **B for now**. The reactive shape is nicer, but the
test impact is real and the current pattern is one-coroutine-line on the
caller side. Revisit if a third "shape-changing setting" lands.

### R11. `DiscoverBrowsersUseCase.selfBundleId` default is wrong

**Files**: `shared/.../domain/usecase/DiscoverBrowsersUseCase.kt:8`.

**Problem**:
```kotlin
class DiscoverBrowsersUseCase(
    private val repository: BrowserRepository,
    private val selfBundleId: String? = null,  // ← defaults to "don't filter"
)
```

Defaulting to `null` means "don't filter out our own bundle from the list".
But that's never what production wants — AppContainer always passes the real
bundle id, and the picker absolutely needs us filtered out (otherwise picking
"Link Opener" loops the URL back into our handler). The default exists
purely to keep tests terse.

**Why it matters**: The default lies about the production contract. A new
caller who reaches for this use case might omit `selfBundleId` and then
mysteriously see the app itself in the picker.

**Variants**:
- **A. Make `selfBundleId` required.** 🟠 — tests pass `null` today;
  signature change cascades.
- **B. Move the "filter self" logic up to `BrowserRepository`** so it's
  applied unconditionally on read. Repository ctor takes `ownBundleId`.
  Same test cascade.
- **C. Leave it.**

**Recommendation**: **C now**, **B if R10 happens** (would naturally land
in the same refactor since BrowserRepository already changes shape).

---

## 4. Summary table

| ID | Item | Effort | Test impact | Recommend |
| --- | --- | ---: | --- | --- |
| **R1** | VM owns its `CoroutineScope` (variant A) | 30 min | ✅ | **Do** |
| **R2** | `applyLocale` → `LocaleApplier` interface (variant B) | 30 min | ✅ | **Do** |
| **R3** | Use-case collapse (B-full) | 1–2 h | 🟠 | **User call** |
| **R4** | `runCatchingNonCancellation` helper | 20 min | ✅ | **Do** |
| **R5** | `PickerCoordinator` log on error | 10 min | ✅ | **Do** |
| **R6** | `set*` vs `update*` rename | 30 min | 🟠 | Bundle into R3 if it happens |
| **R7** | `ExclusionsSection` empty-state → `SectionCard` | 10 min | ✅ | **Do** |
| **R8** | `TrayHost.kt` FQN cleanup | 5 min | ✅ | **Do** |
| **R9** | `SingleInstanceGuard` callback as ctor param | 20 min | ✅ | **Do** |
| **R10** | Reactive `BrowserRepository` (variant A) | 2 h | 🟠 | Defer |
| **R11** | `DiscoverBrowsersUseCase.selfBundleId` required | 15 min | 🟠 | Defer |

Plus old: **#5** (interface split, defer), **#8** (AppContainer subgraphs,
gated on R3).

---

## 5. Suggested execution order

If approved, do the test-safe items together as a single sweep (similar to
round 1's bundling). The 🟠 items are decision-gated and stand alone.

**Sweep 2A — test-safe (~2 h, one PR / squashed commit):**
1. R8 (FQN cleanup — sets the tone)
2. R7 (empty-state → SectionCard)
3. R4 (cancellation helper)
4. R5 (picker error log)
5. R9 (SingleInstanceGuard ctor callback)
6. R1 (VM owns scope)
7. R2 (LocaleApplier interface)

**Decision-gated:**
- **R3** — needs explicit OK on test changes. Biggest single architectural
  win in the codebase.
- **R6**, **R10**, **R11** — defer or bundle into R3.

**Acceptance criteria** (same as round 1):
- [ ] `./gradlew build` is green.
- [ ] `:shared:koverHtmlReport` shows no regression.
- [ ] No test file is modified for sweep 2A.
- [ ] No production behaviour change observable from the UI.
- [ ] Each commit (within the squash) has a one-line subject and a body
      explaining the *why*.
