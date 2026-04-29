package dev.hackathon.linkopener.platform.windows

import dev.hackathon.linkopener.core.model.BrowserFamily
import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WindowsBrowserMetadataExtractorTest {

    private val tempFiles = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // --- parseVersionInfo (pure parser) ---------------------------------

    @Test
    fun parseVersionInfoExtractsThreeFields() {
        val output = """

            ProductName     : Google Chrome
            FileVersion     : 131.0.6778.86
            FileDescription : Google Chrome


        """.trimIndent()

        val fields = WindowsBrowserMetadataExtractor.parseVersionInfo(output)

        assertEquals("Google Chrome", fields["ProductName"])
        assertEquals("131.0.6778.86", fields["FileVersion"])
        assertEquals("Google Chrome", fields["FileDescription"])
    }

    @Test
    fun parseVersionInfoIgnoresBlankLines() {
        val fields = WindowsBrowserMetadataExtractor.parseVersionInfo("\n\nProductName : X\n\n")
        assertEquals(mapOf("ProductName" to "X"), fields)
    }

    @Test
    fun parseVersionInfoToleratesEmptyValues() {
        // Some browsers ship with empty FileDescription; parser yields ""
        // and the caller falls back to ProductName via takeIf { isNotBlank }.
        val fields = WindowsBrowserMetadataExtractor.parseVersionInfo(
            """
                ProductName     : Mozilla Firefox
                FileVersion     : 120.0
                FileDescription :
            """.trimIndent(),
        )
        assertEquals("", fields["FileDescription"])
    }

    // --- extract() (full flow against scripted runner) ------------------

    @Test
    fun extractReturnsFailureWhenFileMissing() = runTest {
        val extractor = WindowsBrowserMetadataExtractor(runner = { error("must not be called") })

        val result = extractor.extract("C:\\does\\not\\exist.exe")

        val failure = assertIs<ExtractResult.Failure>(result)
        assertTrue(failure.reason.contains("not found", ignoreCase = true))
    }

    @Test
    fun extractReturnsFailureForNonExeFile() = runTest {
        val txt = newTempFile(suffix = ".txt")
        val extractor = WindowsBrowserMetadataExtractor(runner = { error("must not be called") })

        val result = extractor.extract(txt.absolutePath)

        val failure = assertIs<ExtractResult.Failure>(result)
        assertTrue(failure.reason.contains("not a windows executable", ignoreCase = true))
    }

    @Test
    fun extractSurfacesPowerShellFailureAsFailure() = runTest {
        val exe = newTempFile(suffix = ".exe")
        val extractor = WindowsBrowserMetadataExtractor(runner = { null })

        val result = extractor.extract(exe.absolutePath)

        val failure = assertIs<ExtractResult.Failure>(result)
        assertTrue(failure.reason.contains("powershell", ignoreCase = true))
    }

    @Test
    fun extractHappyPathBuildsBrowserFromFields() = runTest {
        val exe = newTempFile(suffix = ".exe")
        val extractor = WindowsBrowserMetadataExtractor(
            runner = { args ->
                // Sanity: PowerShell argv shape — `powershell -NoProfile -Command <expr>`.
                assertEquals("powershell", args[0])
                assertEquals("-NoProfile", args[1])
                assertEquals("-Command", args[2])
                """
                    ProductName     : Google Chrome
                    FileVersion     : 131.0.6778.86
                    FileDescription : Google Chrome
                """.trimIndent()
            },
        )

        val result = extractor.extract(exe.absolutePath)

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals("Google Chrome", success.browser.displayName)
        assertEquals("131.0.6778.86", success.browser.version)
        assertEquals(exe.absolutePath, success.browser.applicationPath)
        assertEquals(BrowserFamily.Chromium, success.browser.family)
        // bundleId synthesised from filename without extension.
        assertEquals(exe.nameWithoutExtension, success.browser.bundleId)
    }

    @Test
    fun extractFallsBackToProductNameWhenFileDescriptionBlank() = runTest {
        val exe = newTempFile(suffix = ".exe")
        val extractor = WindowsBrowserMetadataExtractor(
            runner = { _ ->
                """
                    ProductName     : Mozilla Firefox
                    FileVersion     : 120.0
                    FileDescription :
                """.trimIndent()
            },
        )

        val result = extractor.extract(exe.absolutePath)

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals("Mozilla Firefox", success.browser.displayName)
        assertEquals(BrowserFamily.Firefox, success.browser.family)
    }

    @Test
    fun extractFallsBackToFilenameWhenAllNamesBlank() = runTest {
        val exe = newTempFile(suffix = ".exe", prefix = "myapp")
        val extractor = WindowsBrowserMetadataExtractor(
            runner = { "ProductName :\nFileDescription :\nFileVersion : 1.0\n" },
        )

        val result = extractor.extract(exe.absolutePath)

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals(exe.nameWithoutExtension, success.browser.displayName)
        assertEquals("1.0", success.browser.version)
    }

    @Test
    fun extractTreatsMissingVersionAsNull() = runTest {
        val exe = newTempFile(suffix = ".exe")
        val extractor = WindowsBrowserMetadataExtractor(
            runner = { "ProductName : X\nFileDescription : X\n" },
        )

        val result = extractor.extract(exe.absolutePath)

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals(null, success.browser.version)
    }

    private fun newTempFile(suffix: String, prefix: String = "win-meta-"): File =
        File.createTempFile(prefix, suffix).also { tempFiles += it }
}
