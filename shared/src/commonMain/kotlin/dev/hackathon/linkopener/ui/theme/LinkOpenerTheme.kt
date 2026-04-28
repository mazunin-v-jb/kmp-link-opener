package dev.hackathon.linkopener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import dev.hackathon.linkopener.core.model.AppTheme

val LocalIsDarkMode = staticCompositionLocalOf { false }

@Composable
fun LinkOpenerTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val darkMode = resolveDarkMode(theme, isSystemInDarkTheme())
    CompositionLocalProvider(LocalIsDarkMode provides darkMode) {
        MaterialTheme(
            colorScheme = if (darkMode) LinkOpenerDarkColors else LinkOpenerLightColors,
            typography = LinkOpenerTypography,
            content = content,
        )
    }
}
