package dev.hackathon.linkopener.platform.linux

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopEntryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var counter = 0
    private fun fixture(content: String): File {
        // newFile() fails on duplicate names; tests that call fixture()
        // twice (e.g. comment-only / empty pair) need unique names.
        val file = tmp.newFile("entry-${counter++}.desktop")
        file.writeText(content)
        return file
    }

    @Test
    fun parsesBasicKeyValuePairs() {
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                Exec=firefox %u
                MimeType=x-scheme-handler/http;x-scheme-handler/https;
                """.trimIndent(),
            ),
        )!!
        assertEquals("Application", entry.string("Type"))
        assertEquals("Firefox", entry.string("Name"))
        assertEquals("firefox %u", entry.string("Exec"))
        assertEquals(
            listOf("x-scheme-handler/http", "x-scheme-handler/https"),
            entry.semicolonList("MimeType"),
        )
    }

    @Test
    fun ignoresEverythingOutsideDesktopEntryGroup() {
        // The "Open New Private Window" action sub-group also has Name=
        // and Exec= but those should NOT leak into the main group.
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                Exec=firefox %u

                [Desktop Action new-private-window]
                Name=Open a New Private Window
                Exec=firefox --private-window
                """.trimIndent(),
            ),
        )!!
        assertEquals("Firefox", entry.string("Name"))
        assertEquals("firefox %u", entry.string("Exec"))
    }

    @Test
    fun firstOccurrenceWins() {
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=First
                Name=Second
                """.trimIndent(),
            ),
        )!!
        assertEquals("First", entry.string("Name"))
    }

    @Test
    fun localizedStringPicksMostSpecificMatch() {
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=Web Browser
                Name[ru]=Браузер
                Name[ru_RU]=Браузер для России
                """.trimIndent(),
            ),
        )!!
        assertEquals("Браузер для России", entry.localizedString("Name", "ru_RU.UTF-8"))
        assertEquals("Браузер", entry.localizedString("Name", "ru"))
        assertEquals("Web Browser", entry.localizedString("Name", "en_US"))
        assertEquals("Web Browser", entry.localizedString("Name", ""))
    }

    @Test
    fun localizedStringFallsBackToBareKeyWhenNoMatch() {
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=Firefox
                """.trimIndent(),
            ),
        )!!
        assertEquals("Firefox", entry.localizedString("Name", "ru_RU"))
    }

    @Test
    fun returnsNullForCompletelyEmptyOrCommentOnlyFile() {
        assertNull(readDesktopEntry(fixture("")))
        assertNull(readDesktopEntry(fixture("# just a comment\n# nothing else")))
    }

    @Test
    fun returnsNullWhenDesktopEntryGroupAbsent() {
        assertNull(
            readDesktopEntry(
                fixture(
                    """
                    [Some Other Group]
                    Foo=Bar
                    """.trimIndent(),
                ),
            ),
        )
    }

    @Test
    fun ignoresCommentsAndBlankLines() {
        val entry = readDesktopEntry(
            fixture(
                """
                # leading comment

                [Desktop Entry]
                # comment inside the group
                Type=Application

                Name=Firefox
                """.trimIndent(),
            ),
        )!!
        assertEquals("Firefox", entry.string("Name"))
    }

    @Test
    fun booleanRespectsTrueFalseAndDefault() {
        val entry = readDesktopEntry(
            fixture(
                """
                [Desktop Entry]
                Type=Application
                Name=X
                Hidden=true
                NoDisplay=false
                """.trimIndent(),
            ),
        )!!
        assertEquals(true, entry.boolean("Hidden"))
        assertEquals(false, entry.boolean("NoDisplay"))
        assertEquals(true, entry.boolean("MissingKey", default = true))
        assertEquals(false, entry.boolean("MissingKey", default = false))
    }

    @Test
    fun stripExecFieldCodesDropsAllPlaceholderCodes() {
        // %u, %U, %f, %F, %i, %c, %k all dropped; %% becomes literal %.
        assertEquals(
            listOf("firefox"),
            stripExecFieldCodes("firefox %u"),
        )
        assertEquals(
            listOf("/opt/google/chrome/google-chrome"),
            stripExecFieldCodes("/opt/google/chrome/google-chrome %U"),
        )
        assertEquals(
            listOf("foo", "100%"),
            stripExecFieldCodes("foo 100%%"),
        )
    }

    @Test
    fun stripExecFieldCodesRespectsQuotedSegments() {
        assertEquals(
            listOf("/Applications/My App/launcher", "--flag"),
            stripExecFieldCodes("\"/Applications/My App/launcher\" --flag %u"),
        )
    }

    @Test
    fun stripExecFieldCodesHandlesEscapedQuotes() {
        // \" inside a quoted segment becomes a literal "
        assertEquals(
            listOf("say \"hi\""),
            stripExecFieldCodes("\"say \\\"hi\\\"\""),
        )
    }
}
