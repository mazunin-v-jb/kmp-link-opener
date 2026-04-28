package dev.hackathon.linkopener.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import dev.hackathon.linkopener.core.model.AppTheme

@Composable
fun LinkOpenerTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val darkMode = resolveDarkMode(theme, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
