package dev.hackathon.linkopener.ui.settings

/**
 * Top-level Settings sidebar destinations. `internal` so the per-section files
 * and the [Sidebar] composable in `ui.settings.components` can reference the
 * enum without needing to expose it on the public API surface.
 */
internal enum class NavSection { DefaultBrowser, Appearance, Language, System, Exclusions, Rules }
