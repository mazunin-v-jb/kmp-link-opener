package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.core.model.BrowserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxLinkLauncherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(content: String): File {
        val file = tmp.newFile("test.desktop")
        file.writeText(content)
        return file
    }

    private class FakeProcess(val exitCode: Int = 0) : Process() {
        override fun getOutputStream() = error("not used")
        override fun getInputStream() = error("not used")
        override fun getErrorStream() = error("not used")
        override fun waitFor() = exitCode
        override fun exitValue() = exitCode
        override fun destroy() = Unit
    }

    @Test
    fun appendsUrlAfterExecTokens() = runTest {
        val desktop = fixture(
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=/usr/bin/firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val captured = mutableListOf<List<String>>()
        val launcher = LinuxLinkLauncher(processFactory = { args ->
            captured += args
            FakeProcess()
        })

        val ok = launcher.openIn(
            Browser(
                bundleId = "firefox",
                displayName = "Firefox",
                applicationPath = desktop.absolutePath,
                version = null,
                family = BrowserFamily.Firefox,
            ),
            "https://example.com",
        )
        assertTrue(ok)
        assertEquals(listOf(listOf("/usr/bin/firefox", "https://example.com")), captured)
    }

    @Test
    fun stripsFieldCodesFromExec() = runTest {
        val desktop = fixture(
            """
            [Desktop Entry]
            Type=Application
            Name=Chrome
            Exec=/opt/google/chrome/google-chrome --some-flag %U
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val captured = mutableListOf<List<String>>()
        val launcher = LinuxLinkLauncher(processFactory = { args ->
            captured += args
            FakeProcess()
        })
        launcher.openIn(
            Browser(
                bundleId = "google-chrome",
                displayName = "Google Chrome",
                applicationPath = desktop.absolutePath,
                version = null,
                family = BrowserFamily.Chromium,
            ),
            "https://x.test",
        )
        assertEquals(
            listOf(
                listOf(
                    "/opt/google/chrome/google-chrome",
                    "--some-flag",
                    "https://x.test",
                ),
            ),
            captured,
        )
    }

    @Test
    fun chromiumWithProfileAddsProfileDirectoryFlag() = runTest {
        val desktop = fixture(
            """
            [Desktop Entry]
            Type=Application
            Name=Chrome
            Exec=google-chrome %U
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val captured = mutableListOf<List<String>>()
        val launcher = LinuxLinkLauncher(processFactory = { args ->
            captured += args
            FakeProcess()
        })
        launcher.openIn(
            Browser(
                bundleId = "google-chrome",
                displayName = "Google Chrome",
                applicationPath = desktop.absolutePath,
                version = null,
                family = BrowserFamily.Chromium,
                profile = BrowserProfile(id = "Profile 2", displayName = "Work"),
            ),
            "https://issues.test",
        )
        assertEquals(
            listOf(
                listOf(
                    "google-chrome",
                    "--profile-directory=Profile 2",
                    "https://issues.test",
                ),
            ),
            captured,
        )
    }

    @Test
    fun returnsFalseWhenDesktopFileMissing() = runTest {
        val launcher = LinuxLinkLauncher(processFactory = { error("should not spawn") })
        val result = launcher.openIn(
            Browser(
                bundleId = "ghost",
                displayName = "Ghost",
                applicationPath = "/nonexistent/ghost.desktop",
                version = null,
            ),
            "https://x.test",
        )
        assertFalse(result)
    }

    @Test
    fun returnsFalseOnNonZeroExit() = runTest {
        val desktop = fixture(
            """
            [Desktop Entry]
            Type=Application
            Name=X
            Exec=/bin/false %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val launcher = LinuxLinkLauncher(processFactory = { FakeProcess(exitCode = 1) })
        val result = launcher.openIn(
            Browser(
                bundleId = "x",
                displayName = "X",
                applicationPath = desktop.absolutePath,
                version = null,
            ),
            "https://x.test",
        )
        assertFalse(result)
    }
}
