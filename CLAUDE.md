# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A cross-platform Compose Desktop application (macOS / Windows / Linux) that registers itself as a browser and lets the user pick which browser opens each clicked link. Currently in early prototype stage — see `ai_stages/00_overview.md` for the architectural roadmap and `ai_stages/<NN>_<stage>/plan.md` for per-stage plans.

## Workflow rules (from `prompts/1_Prompt.md`)

- **Per-feature branch.** New work for a stage/feature goes in its own branch off `main`. Stage 1 (the prototype scaffolding) is the only thing that lives on `main`.
- **After each iteration:** run all tests, build the artifact, and if everything is green, commit to the current branch. **Do not push** — pushing is the user's call.
- **Tests on every change.** Each feature must keep `./gradlew build` green; add tests for new functionality.
- **Placeholder icons:** when an icon is needed before the final design exists, use a placeholder (programmatic painter or stub asset), mark it with a `TODO`, and explicitly mention it in the chat reply.
- **Sensitive data — STOP AND ASK.** If anything sensitive comes up during work (signing keys, certificates, passwords, API tokens, OAuth client secrets, etc.), flag it **very visibly** in the chat reply and ask the user how to handle it. By default such data must never be committed. If it ends up in the repo by accident, remove it. Sensitive values belong in a separate file (e.g., `secrets.properties` / `gradle.properties` outside VCS) listed in `.gitignore`.
- For each new stage, create `ai_stages/<NN>_<name>/plan.md` describing the implementation in detail before coding.

## Build & test commands

```bash
# Build everything (compile + tests + assemble)
./gradlew build

# Run the desktop app (tray icon + stub menu)
./gradlew :desktopApp:run

# Run shared-module tests
./gradlew :shared:jvmTest

# Run a single test class
./gradlew :shared:jvmTest --tests "dev.hackathon.linkopener.domain.usecase.GetAppInfoUseCaseTest"

# Produce native distributable for the current OS (DMG on macOS, MSI on Windows, DEB on Linux)
./gradlew :desktopApp:packageDistributionForCurrentOS
```

KMP test tasks are named after the target — `:shared:test` does **not** exist; use `:shared:jvmTest` (or `./gradlew test`/`./gradlew check` to run all tests across modules).

## Architecture

Two Gradle modules:

- **`:shared`** — KMP library, JVM target only. Holds domain models, use cases, repository interfaces, platform interfaces, and Compose UI. Source sets: `commonMain`, `commonTest`, `jvmMain`. Package root: `dev.hackathon.linkopener`.
- **`:desktopApp`** — runnable Compose Desktop application. Holds `main()`, the manual composition root (`AppContainer`), and the system tray host. Depends on `:shared`. Package root: `dev.hackathon.linkopener.app`.

Layered structure inside `:shared`:

```
core/    — domain models
domain/  — repository interfaces + use cases (operator fun invoke)
data/    — repository implementations
platform/ — interfaces (BrowserDiscovery, LinkLauncher, ...) with jvmMain impls
            picked at runtime by inspecting System.getProperty("os.name")
ui/      — Compose screens (settings, browser picker, tray menu items)
```

No DI framework — `AppContainer` wires the graph by hand. Platform-specific behavior is selected at runtime, not via expect/actual (all three OSes target the same JVM source set).

## Dependencies

Versions are managed in `gradle/libs.versions.toml`. Compose Multiplatform components are pulled via the Compose plugin's `compose.runtime` / `compose.foundation` / `compose.material3` / `compose.components.resources` accessors (the version-catalog hardcoded artifact for `material3` is not on Maven Central in 1.10.3 — use the plugin shorthand). These shorthands emit deprecation warnings; treat as known and ignore for now.

## Native distributables

`compose.desktop.application.nativeDistributions.packageVersion` must be `MAJOR.MINOR.PATCH` with `MAJOR > 0` (the DMG packager rejects `0.x.y`). The internal `AppInfo` version (returned by `GetAppInfoUseCase`) is independent of the packaging version and can stay at `0.1.0`.

## Things to leave alone

- The repository was originally scaffolded as a Maven Central library template; that publishing config has been removed and **should not be re-added** unless the project is consciously turned back into a library.
- `prompts/1_Prompt.md` is the user's source-of-truth requirement spec — read it for context, but only edit it if the user explicitly asks.
