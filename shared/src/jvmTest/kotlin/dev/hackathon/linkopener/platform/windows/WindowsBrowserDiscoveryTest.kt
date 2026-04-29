package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.BrowserFamily
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsBrowserDiscoveryTest {

    @Test
    fun stripCommandSuffixHandlesQuotedPath() {
        assertEquals(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            WindowsBrowserDiscovery.stripCommandSuffix(
                "\"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\"",
            ),
        )
    }

    @Test
    fun stripCommandSuffixHandlesQuotedPathWithTrailingArgs() {
        assertEquals(
            "C:\\Program Files\\Mozilla Firefox\\firefox.exe",
            WindowsBrowserDiscovery.stripCommandSuffix(
                "\"C:\\Program Files\\Mozilla Firefox\\firefox.exe\" -osint -url \"%1\"",
            ),
        )
    }

    @Test
    fun stripCommandSuffixHandlesUnquotedPathNoSpaces() {
        assertEquals(
            "C:\\Apps\\Chrome\\chrome.exe",
            WindowsBrowserDiscovery.stripCommandSuffix(
                "C:\\Apps\\Chrome\\chrome.exe --single-argument %1",
            ),
        )
    }

    @Test
    fun stripCommandSuffixReturnsNullForEmptyOrInvalid() {
        assertNull(WindowsBrowserDiscovery.stripCommandSuffix(""))
        assertNull(WindowsBrowserDiscovery.stripCommandSuffix("not even a command"))
    }

    @Test
    fun discoverEnumeratesHklmAndHkcuBrowsers() = runTest {
        val registry = ScriptedRegistry()
            .withSubKeys(
                WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET,
                listOf("Google Chrome", "Mozilla Firefox"),
            )
            .withSubKeys(WindowsBrowserDiscovery.HKCU_START_MENU_INTERNET, emptyList())
            .withCommand(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Google Chrome",
                "\"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe\"",
            )
            .withCapabilitiesName(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Google Chrome",
                "Google Chrome",
            )
            .withCommand(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Mozilla Firefox",
                "\"C:\\Program Files\\Mozilla Firefox\\firefox.exe\" -osint -url \"%1\"",
            )

        val browsers = WindowsBrowserDiscovery(registry).discover()

        assertEquals(2, browsers.size)
        // Sorted alphabetically by displayName.
        assertEquals("Google Chrome", browsers[0].displayName)
        assertEquals(
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
            browsers[0].applicationPath,
        )
        assertEquals(BrowserFamily.Chromium, browsers[0].family)
        assertEquals("Mozilla Firefox", browsers[1].displayName)
        assertEquals(BrowserFamily.Firefox, browsers[1].family)
    }

    @Test
    fun discoverDeduplicatesByExePathAcrossHklmAndHkcu() = runTest {
        val sharedExe = "\"C:\\Apps\\Chrome\\chrome.exe\""
        val registry = ScriptedRegistry()
            .withSubKeys(
                WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET,
                listOf("Google Chrome"),
            )
            .withSubKeys(
                WindowsBrowserDiscovery.HKCU_START_MENU_INTERNET,
                listOf("Google Chrome"),
            )
            .withCommand(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Google Chrome",
                sharedExe,
            )
            .withCommand(
                "${WindowsBrowserDiscovery.HKCU_START_MENU_INTERNET}\\Google Chrome",
                sharedExe,
            )

        val browsers = WindowsBrowserDiscovery(registry).discover()

        // Same exe path → one entry. HKLM wins on dedupe order.
        assertEquals(1, browsers.size)
    }

    @Test
    fun discoverSkipsBrowsersWithoutShellOpenCommand() = runTest {
        val registry = ScriptedRegistry()
            .withSubKeys(
                WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET,
                listOf("BrokenInstall", "Working"),
            )
            // BrokenInstall has no command — explicitly omit
            .withCommand(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Working",
                "\"C:\\Apps\\working.exe\"",
            )

        val browsers = WindowsBrowserDiscovery(registry).discover()

        assertEquals(listOf("Working"), browsers.map { it.displayName })
    }

    @Test
    fun discoverFallsBackToSubKeyAsDisplayName() = runTest {
        // Capabilities/ApplicationName missing AND parent key has no
        // (Default) value — fallback chain ends at the subkey name.
        val registry = ScriptedRegistry()
            .withSubKeys(
                WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET,
                listOf("Some Browser"),
            )
            .withCommand(
                "${WindowsBrowserDiscovery.HKLM_START_MENU_INTERNET}\\Some Browser",
                "\"C:\\Apps\\sb.exe\"",
            )

        val browsers = WindowsBrowserDiscovery(registry).discover()

        assertEquals("Some Browser", browsers[0].displayName)
    }

    @Test
    fun discoverReturnsEmptyOnRegistryFailure() = runTest {
        val registry = ScriptedRegistry() // no fixtures → all queries return null
        val browsers = WindowsBrowserDiscovery(registry).discover()
        assertEquals(emptyList(), browsers)
    }

    @Test
    fun discoverDoesNotCrashOnHkcuOnly() = runTest {
        val registry = ScriptedRegistry()
            // HKLM unset (corporate-locked machine)
            .withSubKeys(
                WindowsBrowserDiscovery.HKCU_START_MENU_INTERNET,
                listOf("Google Chrome"),
            )
            .withCommand(
                "${WindowsBrowserDiscovery.HKCU_START_MENU_INTERNET}\\Google Chrome",
                "\"C:\\Users\\u\\AppData\\Local\\Google\\Chrome\\chrome.exe\"",
            )

        val browsers = WindowsBrowserDiscovery(registry).discover()

        assertEquals(1, browsers.size)
        assertEquals("Google Chrome", browsers[0].displayName)
    }

    /**
     * Builds a `RegistryReader` whose injected `runner` returns the
     * scripted output for specific argv lists. Unknown argv → null
     * (mirrors the production "reg.exe failed / key absent" path).
     */
    private class ScriptedRegistry {
        private val outputs = mutableMapOf<List<String>, String>()

        fun withSubKeys(parentPath: String, subKeys: List<String>): ScriptedRegistry = apply {
            // Real reg.exe normalises input hive aliases (HKLM / HKCU /
            // …) to the full HKEY_… form in its output. Mirror that
            // behaviour in the fake so the parser exercises its
            // expansion logic the way it does in production.
            val expanded = RegistryReader.expandHive(parentPath)
            val body = buildString {
                append(expanded).append('\n').append('\n')
                subKeys.forEach { append(expanded).append('\\').append(it).append('\n') }
            }
            outputs[listOf("reg", "query", parentPath)] = body
        }

        fun withCommand(parentPath: String, rawCommand: String): ScriptedRegistry = apply {
            val cmdPath = "$parentPath\\shell\\open\\command"
            val body = "$cmdPath\n    (Default)    REG_SZ    $rawCommand\n"
            // `/ve` queries the default (unnamed) value — see RegistryReader
            // docs for why `/v "(Default)"` doesn't work.
            outputs[listOf("reg", "query", cmdPath, "/ve")] = body
        }

        fun withCapabilitiesName(parentPath: String, applicationName: String): ScriptedRegistry = apply {
            val capPath = "$parentPath\\Capabilities"
            val body = "$capPath\n    ApplicationName    REG_SZ    $applicationName\n"
            outputs[listOf("reg", "query", capPath, "/v", "ApplicationName")] = body
        }

        fun build(): RegistryReader = RegistryReader(runner = { args -> outputs[args] })
    }

    /** Convenience wrapper so the tests read like the original `ScriptedRegistry`-as-RegistryReader pattern. */
    private fun WindowsBrowserDiscovery(scripted: ScriptedRegistry): WindowsBrowserDiscovery =
        WindowsBrowserDiscovery(scripted.build())
}
