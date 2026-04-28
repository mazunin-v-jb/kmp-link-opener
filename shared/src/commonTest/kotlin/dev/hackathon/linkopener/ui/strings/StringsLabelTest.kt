package dev.hackathon.linkopener.ui.strings

import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsLabelTest {

    @Test
    fun englishLabelsForEveryTheme() {
        assertEquals("System", EnglishStrings.label(AppTheme.System))
        assertEquals("Light", EnglishStrings.label(AppTheme.Light))
        assertEquals("Dark", EnglishStrings.label(AppTheme.Dark))
    }

    @Test
    fun russianLabelsForEveryTheme() {
        assertEquals("Системная", RussianStrings.label(AppTheme.System))
        assertEquals("Светлая", RussianStrings.label(AppTheme.Light))
        assertEquals("Тёмная", RussianStrings.label(AppTheme.Dark))
    }

    @Test
    fun languageLabelKeepsNativeNamesRegardlessOfUiLanguage() {
        // Per the comment in Strings.kt, native names ("English" / "Русский")
        // are stable and only "System" follows translation.
        assertEquals("English", EnglishStrings.label(AppLanguage.En))
        assertEquals("Русский", EnglishStrings.label(AppLanguage.Ru))
        assertEquals("English", RussianStrings.label(AppLanguage.En))
        assertEquals("Русский", RussianStrings.label(AppLanguage.Ru))
    }

    @Test
    fun systemLanguageLabelFollowsTranslation() {
        assertEquals("System", EnglishStrings.label(AppLanguage.System))
        assertEquals("Системный", RussianStrings.label(AppLanguage.System))
    }

    @Test
    fun labelMethodsHandleEveryEnumExhaustively() {
        // Compile-time exhaustiveness is enforced by `when` over the sealed enum,
        // but a runtime check makes sure no future addition is silently dropped.
        AppTheme.entries.forEach { theme ->
            val label = EnglishStrings.label(theme)
            assertEquals(true, label.isNotBlank(), "EnglishStrings.label($theme) was blank")
        }
        AppLanguage.entries.forEach { lang ->
            val label = RussianStrings.label(lang)
            assertEquals(true, label.isNotBlank(), "RussianStrings.label($lang) was blank")
        }
    }
}
