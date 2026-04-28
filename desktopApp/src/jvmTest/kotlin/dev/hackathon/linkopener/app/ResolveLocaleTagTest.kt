package dev.hackathon.linkopener.app

import dev.hackathon.linkopener.core.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveLocaleTagTest {

    @Test
    fun explicitEnIgnoresSystem() {
        assertEquals("en", resolveLocaleTag(AppLanguage.En, systemLanguageTag = "ru"))
        assertEquals("en", resolveLocaleTag(AppLanguage.En, systemLanguageTag = "fr"))
        assertEquals("en", resolveLocaleTag(AppLanguage.En, systemLanguageTag = ""))
    }

    @Test
    fun explicitRuIgnoresSystem() {
        assertEquals("ru", resolveLocaleTag(AppLanguage.Ru, systemLanguageTag = "en"))
        assertEquals("ru", resolveLocaleTag(AppLanguage.Ru, systemLanguageTag = "fr"))
    }

    @Test
    fun systemPicksRuWhenHostIsRussian() {
        assertEquals("ru", resolveLocaleTag(AppLanguage.System, systemLanguageTag = "ru"))
    }

    @Test
    fun systemPicksEnWhenHostIsEnglish() {
        assertEquals("en", resolveLocaleTag(AppLanguage.System, systemLanguageTag = "en"))
    }

    @Test
    fun systemFallsBackToEnglishForUnsupportedHostLanguage() {
        // Anything that isn't "ru" → "en". Covers French, German, empty,
        // garbage — we don't want to ship a broken UI for unsupported locales.
        assertEquals("en", resolveLocaleTag(AppLanguage.System, systemLanguageTag = "fr"))
        assertEquals("en", resolveLocaleTag(AppLanguage.System, systemLanguageTag = "de"))
        assertEquals("en", resolveLocaleTag(AppLanguage.System, systemLanguageTag = ""))
        assertEquals("en", resolveLocaleTag(AppLanguage.System, systemLanguageTag = "garbage"))
    }

    @Test
    fun systemTagIsCapturedOnce_notReadBackAfterOverride() {
        // The original bug: after user picked En/Ru, switching back to System
        // re-read Locale.getDefault() (now overridden) and stickied on the
        // last user choice instead of reverting to the OS locale.
        //
        // Simulate the original OS locale captured at startup ("ru"). No
        // matter how many times we re-resolve En → Ru → System within a
        // session, System must keep returning "ru".
        val capturedSystemTag = "ru"

        // Sequence: System (boot) → En → Ru → System → En → System.
        assertEquals("ru", resolveLocaleTag(AppLanguage.System, capturedSystemTag))
        assertEquals("en", resolveLocaleTag(AppLanguage.En, capturedSystemTag))
        assertEquals("ru", resolveLocaleTag(AppLanguage.Ru, capturedSystemTag))
        assertEquals("ru", resolveLocaleTag(AppLanguage.System, capturedSystemTag))
        assertEquals("en", resolveLocaleTag(AppLanguage.En, capturedSystemTag))
        assertEquals("ru", resolveLocaleTag(AppLanguage.System, capturedSystemTag))
    }
}
