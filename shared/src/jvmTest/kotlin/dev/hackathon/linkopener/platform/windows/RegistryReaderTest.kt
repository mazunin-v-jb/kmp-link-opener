package dev.hackathon.linkopener.platform.windows

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the parsing helpers on [RegistryReader.Companion]. Output
 * fixtures captured from real `reg.exe` invocations on Windows 10/11.
 *
 * The instance methods (`query`, `setValue`, `deleteValue`) shell out
 * and are smoke-tested separately; here we focus on the parser, which
 * runs on every host.
 */
class RegistryReaderTest {

    @Test
    fun parseSubKeysReturnsImmediateChildrenOnly() {
        val output = """
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet

            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Mozilla Firefox
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Microsoft Edge
        """.trimIndent()

        val keys = RegistryReader.parseSubKeys(
            output,
            "HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet",
        )

        assertEquals(listOf("Google Chrome", "Mozilla Firefox", "Microsoft Edge"), keys)
    }

    @Test
    fun parseSubKeysSkipsDeeplyNestedLines() {
        // /s recursive output includes nested keys; we want only direct children.
        val output = """
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome\shell\open\command
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Mozilla Firefox
        """.trimIndent()

        val keys = RegistryReader.parseSubKeys(
            output,
            "HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet",
        )

        assertEquals(listOf("Google Chrome", "Mozilla Firefox"), keys)
    }

    @Test
    fun parseSubKeysReturnsEmptyOnEmptyOutput() {
        assertEquals(emptyList(), RegistryReader.parseSubKeys("", "HKLM\\Anything"))
    }

    @Test
    fun parseSingleValueExtractsDefault() {
        val output = """
            HKEY_LOCAL_MACHINE\SOFTWARE\Clients\StartMenuInternet\Google Chrome\shell\open\command
                (Default)    REG_SZ    "C:\Program Files\Google\Chrome\Application\chrome.exe"

        """.trimIndent()

        val value = RegistryReader.parseSingleValue(output, name = "")

        // Quotes preserved verbatim (callers strip via stripCommandSuffix).
        assertEquals(
            "\"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\"",
            value,
        )
    }

    @Test
    fun parseSingleValueExtractsNamedValue() {
        val output = """
            HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice
                Hash    REG_SZ    abcdef
                ProgId  REG_SZ    ChromeHTML

        """.trimIndent()

        assertEquals("ChromeHTML", RegistryReader.parseSingleValue(output, name = "ProgId"))
        assertEquals("abcdef", RegistryReader.parseSingleValue(output, name = "Hash"))
    }

    @Test
    fun parseSingleValueIsCaseInsensitiveOnName() {
        // Sometimes reg.exe casing varies between Windows versions; be
        // defensive on the value-name side. The data column stays
        // case-preserved.
        val output = """
            HKEY_LOCAL_MACHINE\Foo
                progid    REG_SZ    LinkOpener.URL

        """.trimIndent()

        assertEquals("LinkOpener.URL", RegistryReader.parseSingleValue(output, name = "ProgId"))
    }

    @Test
    fun parseSingleValueReturnsNullForMissingName() {
        val output = """
            HKEY_LOCAL_MACHINE\Foo
                Other    REG_SZ    bar

        """.trimIndent()

        assertNull(RegistryReader.parseSingleValue(output, name = "Missing"))
    }

    @Test
    fun parseSingleValuePreservesQuotesVerbatim() {
        // reg.exe wraps any REG_SZ string containing spaces in quotes.
        // We DON'T auto-strip — for shell\open\command values the
        // leading `"` is part of a quoted path followed by unquoted
        // argv tokens (e.g. `"path" -osint "%1"`), so unconditional
        // stripping would mangle the data. Callers use
        // WindowsBrowserDiscovery.stripCommandSuffix when they want
        // a clean path.
        val output = """
            HKEY_LOCAL_MACHINE\Foo
                Path    REG_SZ    "C:\Program Files\X\y.exe"

        """.trimIndent()

        assertEquals(
            "\"C:\\Program Files\\X\\y.exe\"",
            RegistryReader.parseSingleValue(output, "Path"),
        )
    }

    @Test
    fun parseSingleValueLeavesUnquotedSimpleValues() {
        val output = """
            HKEY_LOCAL_MACHINE\Foo
                Name    REG_SZ    Bar

        """.trimIndent()

        assertEquals("Bar", RegistryReader.parseSingleValue(output, "Name"))
    }

    @Test
    fun parseSingleValueHandlesDifferentRegTypes() {
        val output = """
            HKEY_LOCAL_MACHINE\Foo
                Count    REG_DWORD    0x00000005
                Path     REG_EXPAND_SZ    %ProgramFiles%\X
                Bytes    REG_BINARY    01020304

        """.trimIndent()

        assertEquals("0x00000005", RegistryReader.parseSingleValue(output, "Count"))
        assertEquals("%ProgramFiles%\\X", RegistryReader.parseSingleValue(output, "Path"))
        assertEquals("01020304", RegistryReader.parseSingleValue(output, "Bytes"))
    }
}
