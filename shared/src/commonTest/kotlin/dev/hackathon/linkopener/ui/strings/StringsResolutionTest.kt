package dev.hackathon.linkopener.ui.strings

import dev.hackathon.linkopener.core.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StringsResolutionTest {

    @Test
    fun explicitEnglishAlwaysReturnsEnglish() {
        assertSame(EnglishStrings, resolveStrings(AppLanguage.En, systemLanguageTag = null))
        assertSame(EnglishStrings, resolveStrings(AppLanguage.En, systemLanguageTag = "ru"))
        assertSame(EnglishStrings, resolveStrings(AppLanguage.En, systemLanguageTag = "fr"))
    }

    @Test
    fun explicitRussianAlwaysReturnsRussian() {
        assertSame(RussianStrings, resolveStrings(AppLanguage.Ru, systemLanguageTag = null))
        assertSame(RussianStrings, resolveStrings(AppLanguage.Ru, systemLanguageTag = "en"))
    }

    @Test
    fun systemFallsBackToEnglishWhenTagIsNull() {
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = null))
    }

    @Test
    fun systemPicksRussianForRuTag() {
        assertSame(RussianStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "ru"))
    }

    @Test
    fun systemPicksEnglishForEnTag() {
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "en"))
    }

    @Test
    fun systemTagIsCaseInsensitive() {
        assertSame(RussianStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "RU"))
        assertSame(RussianStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "Ru"))
    }

    @Test
    fun systemTagAcceptsRegionSubtags() {
        assertSame(RussianStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "ru-RU"))
        assertSame(RussianStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "ru_RU"))
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "en-US"))
    }

    @Test
    fun systemFallsBackToEnglishForUnknownLanguages() {
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "fr"))
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "de"))
        assertSame(EnglishStrings, resolveStrings(AppLanguage.System, systemLanguageTag = "ja"))
    }

    @Test
    fun englishStringsCoverCriticalEntries() {
        assertEquals("Settings", EnglishStrings.settingsTitle)
        assertEquals("Quit", EnglishStrings.trayMenuQuit)
        assertEquals("System", EnglishStrings.themeSystem)
    }

    @Test
    fun russianStringsCoverCriticalEntries() {
        assertEquals("Настройки", RussianStrings.settingsTitle)
        assertEquals("Выход", RussianStrings.trayMenuQuit)
        assertEquals("Системная", RussianStrings.themeSystem)
    }
}
