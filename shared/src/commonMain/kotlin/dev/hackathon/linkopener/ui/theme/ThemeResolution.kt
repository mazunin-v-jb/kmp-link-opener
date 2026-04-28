package dev.hackathon.linkopener.ui.theme

import dev.hackathon.linkopener.core.model.AppTheme

fun resolveDarkMode(theme: AppTheme, systemInDarkMode: Boolean): Boolean = when (theme) {
    AppTheme.System -> systemInDarkMode
    AppTheme.Light -> false
    AppTheme.Dark -> true
}
