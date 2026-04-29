package dev.hackathon.linkopener.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Tonal surface helpers shared across the Settings and picker screens.
 * Material 3's `surfaceContainerLow` / `surfaceContainerLowest` aren't part
 * of the Compose Multiplatform color scheme yet, so we expose our own
 * design-system tones via [LocalIsDarkMode] (set by [LinkOpenerTheme]).
 */
@Composable
@ReadOnlyComposable
fun surfaceContainerLow(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLow else LightSurfaceContainerLow

@Composable
@ReadOnlyComposable
fun surfaceContainerLowest(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLowest else LightSurfaceContainerLowest
