package dev.hackathon.linkopener.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Hex values copy-pasted from
// ~/Desktop/design/stitch_link_opener_settings_design/link_opener_design_system/DESIGN.md
// (frontmatter `colors:` block). Source of truth.

private val LightPrimary = Color(0xFF003178)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFF0D47A1)
private val LightOnPrimaryContainer = Color(0xFFA1BBFF)
private val LightInversePrimary = Color(0xFFB0C6FF)
private val LightSecondary = Color(0xFF48626E)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFCBE7F5)
private val LightOnSecondaryContainer = Color(0xFF4E6874)
private val LightTertiary = Color(0xFF003D35)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFF00564C)
private val LightOnTertiaryContainer = Color(0xFF70CCBC)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF93000A)
private val LightBackground = Color(0xFFF8F9FB)
private val LightOnBackground = Color(0xFF191C1E)
private val LightSurface = Color(0xFFF8F9FB)
private val LightOnSurface = Color(0xFF191C1E)
private val LightSurfaceVariant = Color(0xFFE0E3E5)
private val LightOnSurfaceVariant = Color(0xFF434652)
private val LightOutline = Color(0xFF737783)
private val LightOutlineVariant = Color(0xFFC3C6D4)
private val LightInverseSurface = Color(0xFF2D3133)
private val LightInverseOnSurface = Color(0xFFEFF1F3)
private val LightSurfaceTint = Color(0xFF2B5BB5)
internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF2F4F6)
internal val LightSurfaceContainer = Color(0xFFECEEF0)
internal val LightSurfaceContainerHigh = Color(0xFFE6E8EA)
internal val LightSurfaceContainerHighest = Color(0xFFE0E3E5)

// Dark scheme is derived from the design system. The spec text states
// dark surface uses a deep charcoal (#121212) with primary-tinted overlays;
// primary swaps to inverse-primary; surfaceVariant uses what was inverse-surface
// in light mode.
private val DarkPrimary = Color(0xFFB0C6FF)
private val DarkOnPrimary = Color(0xFF002A77)
private val DarkPrimaryContainer = Color(0xFF00429C)
private val DarkOnPrimaryContainer = Color(0xFFD9E2FF)
private val DarkInversePrimary = Color(0xFF003178)
private val DarkSecondary = Color(0xFFAFCBD8)
private val DarkOnSecondary = Color(0xFF1A343F)
private val DarkSecondaryContainer = Color(0xFF304A55)
private val DarkOnSecondaryContainer = Color(0xFFCBE7F5)
private val DarkTertiary = Color(0xFF7AD7C6)
private val DarkOnTertiary = Color(0xFF003730)
private val DarkTertiaryContainer = Color(0xFF005047)
private val DarkOnTertiaryContainer = Color(0xFF97F3E2)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF121212)
private val DarkOnBackground = Color(0xFFE2E2E5)
private val DarkSurface = Color(0xFF121212)
private val DarkOnSurface = Color(0xFFE2E2E5)
private val DarkSurfaceVariant = Color(0xFF2D3133)
private val DarkOnSurfaceVariant = Color(0xFFC3C6D4)
private val DarkOutline = Color(0xFF8C8F9B)
private val DarkOutlineVariant = Color(0xFF434652)
private val DarkInverseSurface = Color(0xFFE2E2E5)
private val DarkInverseOnSurface = Color(0xFF2D3133)
private val DarkSurfaceTint = Color(0xFFB0C6FF)
internal val DarkSurfaceContainerLowest = Color(0xFF0B0B0B)
internal val DarkSurfaceContainerLow = Color(0xFF1A1A1C)
internal val DarkSurfaceContainer = Color(0xFF1E1E20)
internal val DarkSurfaceContainerHigh = Color(0xFF282828)
internal val DarkSurfaceContainerHighest = Color(0xFF333335)

val LinkOpenerLightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    surfaceTint = LightSurfaceTint,
)

val LinkOpenerDarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    surfaceTint = DarkSurfaceTint,
)
