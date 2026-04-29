package dev.hackathon.linkopener.platform.windows

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsDefaultBrowserServiceTest {

    @Test
    fun isDefaultBrowserTrueWhenProgIdMatches() = runTest {
        val registry = ScriptedRunner()
            .with(
                listOf(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
                    "/v", "ProgId",
                ),
                """
                    HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice
                        ProgId    REG_SZ    LinkOpener.URL

                """.trimIndent(),
            )
        val service = WindowsDefaultBrowserService(registry.build())

        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserFalseWhenProgIdIsAnotherApp() = runTest {
        val registry = ScriptedRunner()
            .with(
                listOf(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
                    "/v", "ProgId",
                ),
                """
                    HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice
                        ProgId    REG_SZ    ChromeHTML

                """.trimIndent(),
            )
        val service = WindowsDefaultBrowserService(registry.build())

        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserFalseWhenUserChoiceMissing() = runTest {
        // Brand-new Windows install before the user has touched the
        // default-app picker. UserChoice key doesn't exist yet.
        val registry = ScriptedRunner() // no scripts → all queries return null
        val service = WindowsDefaultBrowserService(registry.build())

        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserMatchIsCaseInsensitive() = runTest {
        // Registry preserves casing in REG_SZ data; comparison should
        // tolerate `linkopener.url` vs `LinkOpener.URL`.
        val registry = ScriptedRunner()
            .with(
                listOf(
                    "reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\http\\UserChoice",
                    "/v", "ProgId",
                ),
                """
                    HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\Shell\Associations\UrlAssociations\http\UserChoice
                        ProgId    REG_SZ    LINKOPENER.URL

                """.trimIndent(),
            )
        val service = WindowsDefaultBrowserService(registry.build())

        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun canOpenSystemSettingsTrue() {
        // No subprocess invocation here; just the contract-level read.
        val service = WindowsDefaultBrowserService()
        assertEquals(true, service.canOpenSystemSettings)
    }

    private class ScriptedRunner {
        private val outputs = mutableMapOf<List<String>, String>()
        fun with(args: List<String>, output: String): ScriptedRunner = apply { outputs[args] = output }
        fun build(): RegistryReader = RegistryReader(runner = { args -> outputs[args] })
    }
}
