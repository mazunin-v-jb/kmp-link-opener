package dev.hackathon.linkopener.ui.theme

import dev.hackathon.linkopener.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeResolutionTest {

    @Test
    fun systemThemeFollowsSystemDarkMode() {
        assertTrue(resolveDarkMode(AppTheme.System, systemInDarkMode = true))
        assertFalse(resolveDarkMode(AppTheme.System, systemInDarkMode = false))
    }

    @Test
    fun lightThemeIsAlwaysLight() {
        assertFalse(resolveDarkMode(AppTheme.Light, systemInDarkMode = true))
        assertFalse(resolveDarkMode(AppTheme.Light, systemInDarkMode = false))
    }

    @Test
    fun darkThemeIsAlwaysDark() {
        assertTrue(resolveDarkMode(AppTheme.Dark, systemInDarkMode = true))
        assertTrue(resolveDarkMode(AppTheme.Dark, systemInDarkMode = false))
    }

    @Test
    fun matrixIsExhaustive() {
        val matrix = listOf(
            AppTheme.System to true to true,
            AppTheme.System to false to false,
            AppTheme.Light to true to false,
            AppTheme.Light to false to false,
            AppTheme.Dark to true to true,
            AppTheme.Dark to false to true,
        )
        matrix.forEach { (input, expected) ->
            val (theme, systemDark) = input
            assertEquals(expected, resolveDarkMode(theme, systemDark), "theme=$theme system=$systemDark")
        }
    }
}
