package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxBrowserMetadataExtractorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(name: String, body: String): File =
        File(tmp.newFolder(), name).apply { writeText(body) }

    @Test
    fun extractsHttpHandler() = runTest {
        val file = fixture(
            "firefox.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=Firefox
            Exec=firefox %u
            MimeType=x-scheme-handler/http;x-scheme-handler/https;
            """.trimIndent(),
        )
        val result = LinuxBrowserMetadataExtractor().extract(file.absolutePath)
        assertTrue(result is ExtractResult.Success, result.toString())
        val browser = (result as ExtractResult.Success).browser
        assertEquals("firefox", browser.bundleId)
        assertEquals("Firefox", browser.displayName)
        assertEquals(file.canonicalPath, browser.applicationPath)
    }

    @Test
    fun rejectsNonExistentPath() = runTest {
        val result = LinuxBrowserMetadataExtractor()
            .extract("/nonexistent/foo.desktop")
        assertTrue(result is ExtractResult.Failure)
    }

    @Test
    fun rejectsNonDesktopExtension() = runTest {
        val file = fixture("firefox.txt", "anything")
        val result = LinuxBrowserMetadataExtractor().extract(file.absolutePath)
        assertTrue(result is ExtractResult.Failure)
    }

    @Test
    fun rejectsEntriesWithoutHttpMimeType() = runTest {
        val file = fixture(
            "gimp.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=GIMP
            Exec=gimp %F
            MimeType=image/png;image/jpeg;
            """.trimIndent(),
        )
        val result = LinuxBrowserMetadataExtractor().extract(file.absolutePath)
        assertTrue(result is ExtractResult.Failure)
        assertTrue((result as ExtractResult.Failure).reason.contains("http/https"))
    }

    @Test
    fun rejectsServiceTypeEntries() = runTest {
        val file = fixture(
            "service.desktop",
            """
            [Desktop Entry]
            Type=Service
            Name=Some Service
            Exec=/usr/bin/some-service
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val result = LinuxBrowserMetadataExtractor().extract(file.absolutePath)
        assertTrue(result is ExtractResult.Failure)
    }

    @Test
    fun rejectsEntriesMissingExec() = runTest {
        val file = fixture(
            "noexec.desktop",
            """
            [Desktop Entry]
            Type=Application
            Name=NoExec
            MimeType=x-scheme-handler/http;
            """.trimIndent(),
        )
        val result = LinuxBrowserMetadataExtractor().extract(file.absolutePath)
        assertTrue(result is ExtractResult.Failure)
    }
}
