package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.domain.BrowserMetadataExtractor.ExtractResult
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MacOsBrowserMetadataExtractorTest {

    private val tempDirs = mutableListOf<Path>()

    @AfterTest
    fun cleanupTempDirs() {
        tempDirs.forEach { dir ->
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
        tempDirs.clear()
    }

    @Test
    fun reportsFailureWhenPathDoesNotExist() = runTest {
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = failingRunner("runner should not run for non-existent path"),
        )

        val result = extractor.extract("/no/such/path/Bogus.app")

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("does not exist"))
    }

    @Test
    fun reportsFailureWhenNotAnAppBundle() = runTest {
        val notApp = newTempDir().resolve("PlainDir").also { Files.createDirectories(it) }
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = failingRunner("runner should not run for non-.app path"),
        )

        val result = extractor.extract(notApp.toString())

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains(".app"))
    }

    @Test
    fun reportsFailureWhenInfoPlistMissing() = runTest {
        val app = newTempDir().resolve("NoPlist.app").also { Files.createDirectories(it) }
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = failingRunner("runner should not run when Info.plist is missing"),
        )

        val result = extractor.extract(app.toString())

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("Info.plist"))
    }

    @Test
    fun reportsFailureWhenPlutilReturnsNull() = runTest {
        val app = appBundleWithPlist("anything")
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = stubRunner { null },
        )

        val result = extractor.extract(app.toString())

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("plutil"))
    }

    @Test
    fun reportsFailureWhenInfoPlistMissesBundleIdentifier() = runTest {
        val app = appBundleWithPlist("ignored")
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = stubRunner { """{"CFBundleName":"X"}""" },
        )

        val result = extractor.extract(app.toString())

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("CFBundleIdentifier"))
    }

    @Test
    fun reportsFailureWhenAppDoesNotDeclareHttpHandler() = runTest {
        val app = appBundleWithPlist("ignored")
        // Has a CFBundleIdentifier (so not the missing-id failure), but no
        // CFBundleURLTypes / no http(s) under it — Calculator.app shape.
        val plistJson = """
            {
              "CFBundleIdentifier": "com.example.calculator",
              "CFBundleDisplayName": "Calculator"
            }
        """.trimIndent()
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = stubRunner { plistJson },
        )

        val result = extractor.extract(app.toString())

        val failure = assertIs<ExtractResult.Failure>(result)
        assertEquals(true, failure.reason.contains("http"))
    }

    @Test
    fun returnsBrowserOnSuccess() = runTest {
        val app = appBundleWithPlist("ignored")
        val plistJson = """
            {
              "CFBundleIdentifier": "com.example.fake",
              "CFBundleDisplayName": "Fake Browser",
              "CFBundleShortVersionString": "2.5",
              "CFBundleURLTypes": [
                {
                  "CFBundleURLName": "Web URL",
                  "CFBundleURLSchemes": ["http", "https"]
                }
              ]
            }
        """.trimIndent()
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = stubRunner { plistJson },
        )

        val result = extractor.extract(app.toString())

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals("com.example.fake", success.browser.bundleId)
        assertEquals("Fake Browser", success.browser.displayName)
        assertEquals("2.5", success.browser.version)
        assertEquals(app.toString(), success.browser.applicationPath)
    }

    @Test
    fun fallsBackToFolderNameWhenNoBundleNames() = runTest {
        val app = appBundleWithPlist("ignored", folderName = "MyCustom.app")
        val plistJson = """
            {
              "CFBundleIdentifier": "com.example.bare",
              "CFBundleURLTypes": [
                { "CFBundleURLSchemes": ["http", "https"] }
              ]
            }
        """.trimIndent()
        val extractor = MacOsBrowserMetadataExtractor(
            plutilRunner = stubRunner { plistJson },
        )

        val result = extractor.extract(app.toString())

        val success = assertIs<ExtractResult.Success>(result)
        assertEquals("MyCustom", success.browser.displayName)
    }

    // --- helpers ---

    private fun newTempDir(): Path =
        Files.createTempDirectory("manual-extractor-test").also { tempDirs.add(it) }

    private fun appBundleWithPlist(plistContent: String, folderName: String = "Sample.app"): Path {
        val app = newTempDir().resolve(folderName)
        val contents = app.resolve("Contents")
        Files.createDirectories(contents)
        contents.resolve("Info.plist").writeText(plistContent)
        return app
    }

    private fun stubRunner(block: (Path) -> String?): PlutilRunner =
        object : PlutilRunner() {
            override fun toJson(plistPath: Path): String? = block(plistPath)
        }

    private fun failingRunner(message: String): PlutilRunner =
        object : PlutilRunner() {
            override fun toJson(plistPath: Path): String? = error(message)
        }
}
