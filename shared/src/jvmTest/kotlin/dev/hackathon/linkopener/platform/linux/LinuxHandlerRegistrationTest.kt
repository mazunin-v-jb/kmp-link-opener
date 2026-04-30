package dev.hackathon.linkopener.platform.linux

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxHandlerRegistrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun registerWritesDesktopFileWithExecAndMimeTypes() = runTest {
        val applicationsDir = tmp.newFolder("applications").toPath()
        var refreshedDir: Path? = null
        val registration = LinuxHandlerRegistration(
            applicationsDir = applicationsDir,
            launchTokensProvider = { listOf("/usr/bin/java", "-jar", "/opt/link-opener.jar") },
            updateDesktopDatabaseRunner = { refreshedDir = it },
        )

        assertTrue(registration.register())
        val written = applicationsDir.resolve("link-opener.desktop")
        assertTrue(Files.exists(written))
        val body = Files.readString(written)
        assertTrue(body.contains("Type=Application"))
        assertTrue(body.contains("Exec=/usr/bin/java -jar /opt/link-opener.jar %u"))
        // text/html alongside http/https — required for xdg-settings
        // check default-web-browser to recognise us.
        assertTrue(body.contains("MimeType=x-scheme-handler/http;x-scheme-handler/https;text/html;"))
        assertTrue(body.contains("Categories=Network;WebBrowser;"))
        assertEquals(applicationsDir, refreshedDir)
    }

    @Test
    fun registerIsIdempotent() = runTest {
        val applicationsDir = tmp.newFolder("applications").toPath()
        val registration = LinuxHandlerRegistration(
            applicationsDir = applicationsDir,
            launchTokensProvider = { listOf("/usr/bin/link-opener") },
            updateDesktopDatabaseRunner = { },
        )
        assertTrue(registration.register())
        assertTrue(registration.register())
        // Body unchanged after the rewrite.
        val body = Files.readString(applicationsDir.resolve("link-opener.desktop"))
        assertEquals(1, body.split("Type=Application").size - 1)
    }

    @Test
    fun registerSkipsWriteWhenLaunchTokensUnavailable() = runTest {
        val applicationsDir = tmp.newFolder("applications").toPath()
        val registration = LinuxHandlerRegistration(
            applicationsDir = applicationsDir,
            launchTokensProvider = { null },
            updateDesktopDatabaseRunner = { error("should not be invoked") },
        )
        assertFalse(registration.register())
        assertFalse(Files.exists(applicationsDir.resolve("link-opener.desktop")))
    }
}
