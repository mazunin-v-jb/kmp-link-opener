package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.core.model.BrowserFamily
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxBrowserDiscoveryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun File.dropDesktop(name: String, body: String) {
        val applications = resolve("applications").apply { mkdirs() }
        File(applications, name).writeText(body)
    }

    @Test
    fun discoversBrowsersWithHttpHandlerMimeType() = runTest {
        val data = tmp.newFolder("data")
        data.dropDesktop(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=firefox %u
            MimeType=x-scheme-handler/http;x-scheme-handler/https;text/html;
            """.trimIndent(),
        )
        data.dropDesktop(
            "google-chrome.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Google Chrome
            Exec=/opt/google/chrome/google-chrome %U
            MimeType=text/html;application/xhtml+xml;x-scheme-handler/http;
            """.trimIndent(),
        )

        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(data.absolutePath) },
            xdgDataHome = { tmp.newFolder("home").absolutePath },
        )

        val results = discovery.discover()
        assertEquals(2, results.size)
        // Sort is by displayName.lowercase(): "firefox" < "google chrome".
        assertEquals(listOf("Firefox", "Google Chrome"), results.map { it.displayName })
        assertEquals("firefox", results[0].bundleId)
        assertEquals(BrowserFamily.Firefox, results[0].family)
        assertEquals(BrowserFamily.Chromium, results[1].family)
    }

    @Test
    fun skipsHiddenAndNoDisplay() = runTest {
        val data = tmp.newFolder("data")
        data.dropDesktop(
            "hidden.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Hidden Browser
            Exec=hidden %u
            MimeType=x-scheme-handler/http;
            Hidden=true
            """.trimIndent(),
        )
        data.dropDesktop(
            "nodisplay.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=NoDisplay Browser
            Exec=nodisplay %u
            MimeType=x-scheme-handler/http;
            NoDisplay=true
            """.trimIndent(),
        )

        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(data.absolutePath) },
            xdgDataHome = { tmp.newFolder("home").absolutePath },
        )
        assertTrue(discovery.discover().isEmpty())
    }

    @Test
    fun skipsNonApplicationsAndNonBrowsers() = runTest {
        val data = tmp.newFolder("data")
        data.dropDesktop(
            "service.desktop",
            """
            [Desktop Entry]
            Type=Service
            Name=Some Service
            Exec=/usr/bin/some-service
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        data.dropDesktop(
            "gimp.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=GIMP
            Exec=gimp %F
            MimeType=image/png;image/jpeg;
            """.trimIndent(),
        )
        data.dropDesktop(
            "no-mime.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=No MimeType
            Exec=foo %u
            """.trimIndent(),
        )

        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(data.absolutePath) },
            xdgDataHome = { tmp.newFolder("home").absolutePath },
        )
        assertTrue(discovery.discover().isEmpty())
    }

    @Test
    fun userOverrideWinsOverSystemEntry() = runTest {
        // Same bundleId in user dir + system dir; user dir wins via
        // `putIfAbsent` (we walk user dir first).
        val systemDir = tmp.newFolder("system")
        val userDir = tmp.newFolder("home")
        // Same applicationPath -> dedupe by canonical path. Use
        // identical .desktop content but the user dir's wins because
        // we visit it first.
        systemDir.dropDesktop(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=System Firefox
            Exec=/usr/bin/firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        userDir.dropDesktop(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=User Firefox
            Exec=/home/me/.local/firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )

        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(systemDir.absolutePath) },
            xdgDataHome = { userDir.absolutePath },
        )
        val results = discovery.discover()
        assertEquals(1, results.size)
        assertEquals("User Firefox", results[0].displayName)
    }

    @Test
    fun bareNameUsedEvenWhenLocalisedVariantsExist() = runTest {
        val data = tmp.newFolder("data")
        data.dropDesktop(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Name[ru]=Огненный лис
            Exec=firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )

        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(data.absolutePath) },
            xdgDataHome = { tmp.newFolder("home").absolutePath },
        )
        // Localisation is no longer applied — bare `Name=` always wins.
        assertEquals("Firefox", discovery.discover().single().displayName)
    }

    @Test
    fun emptyDirsYieldEmpty() = runTest {
        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { emptyList() },
            xdgDataHome = { "/nonexistent/path" },
        )
        assertTrue(discovery.discover().isEmpty())
    }

    @Test
    fun applicationPathIsCanonical() = runTest {
        val data = tmp.newFolder("data")
        data.dropDesktop(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=firefox %u
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val discovery = LinuxBrowserDiscovery(
            xdgDataDirs = { listOf(data.absolutePath) },
            xdgDataHome = { tmp.newFolder("home").absolutePath },
        )
        val browser = discovery.discover().single()
        assertEquals(
            File(data, "applications/firefox.desktop").canonicalPath,
            browser.applicationPath,
        )
        assertNull(browser.profile)
    }
}
