package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class InfoPlistReaderTest {

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
    fun returnsNullWhenInfoPlistFileMissing() {
        val appBundle = newTempDir().resolve("Sample.app").also { Files.createDirectories(it) }
        // No Contents/Info.plist created.
        val reader = InfoPlistReader(
            runner = failingRunner("runner should not be invoked when Info.plist is missing"),
            parser = failingParser("parser should not be invoked when Info.plist is missing"),
        )

        val result = reader.readBrowser(appBundle)

        assertNull(result)
    }

    @Test
    fun returnsNullWhenRunnerReturnsNull() {
        val appBundle = appBundleWithPlist()
        val reader = InfoPlistReader(
            runner = stubRunner { null },
            parser = failingParser("parser should not be invoked when runner returns null"),
        )

        val result = reader.readBrowser(appBundle)

        assertNull(result)
    }

    @Test
    fun delegatesToParserOnSuccessAndForwardsAppBundlePath() {
        val appBundle = appBundleWithPlist()
        val expected = Browser(
            bundleId = "com.example.browser",
            displayName = "Example",
            applicationPath = appBundle.toString(),
            version = "1.0",
        )
        val seenJson = mutableListOf<String>()
        val seenAppPath = mutableListOf<Path>()
        val reader = InfoPlistReader(
            runner = stubRunner { """{"CFBundleIdentifier":"com.example.browser"}""" },
            parser = stubParser { jsonText, appPath ->
                seenJson.add(jsonText)
                seenAppPath.add(appPath)
                expected
            },
        )

        val result = reader.readBrowser(appBundle)

        assertSame(expected, result)
        assertEquals(1, seenJson.size)
        assertEquals(1, seenAppPath.size)
        assertEquals(appBundle, seenAppPath.single())
        assertEquals("""{"CFBundleIdentifier":"com.example.browser"}""", seenJson.single())
    }

    @Test
    fun returnsParserNullDecisionThrough() {
        val appBundle = appBundleWithPlist()
        val reader = InfoPlistReader(
            runner = stubRunner { """{"not":"a browser"}""" },
            parser = stubParser { _, _ -> null },
        )

        val result = reader.readBrowser(appBundle)

        assertNull(result)
    }

    @Test
    fun forwardsContentsInfoPlistPathToRunner() {
        val appBundle = appBundleWithPlist()
        val seenPlistPath = mutableListOf<Path>()
        val reader = InfoPlistReader(
            runner = object : PlutilRunner() {
                override fun toJson(plistPath: Path): String? {
                    seenPlistPath.add(plistPath)
                    return null
                }
            },
            parser = failingParser("parser should not be invoked"),
        )

        reader.readBrowser(appBundle)

        assertEquals(
            appBundle.resolve("Contents").resolve("Info.plist"),
            seenPlistPath.single(),
        )
    }

    // --- helpers ---

    private fun newTempDir(): Path =
        Files.createTempDirectory("info-plist-reader-test").also { tempDirs.add(it) }

    private fun appBundleWithPlist(): Path {
        val appBundle = newTempDir().resolve("Sample.app")
        val contents = appBundle.resolve("Contents")
        Files.createDirectories(contents)
        contents.resolve("Info.plist").writeText("<plist/>")
        return appBundle
    }

    private fun stubRunner(block: (Path) -> String?): PlutilRunner =
        object : PlutilRunner() {
            override fun toJson(plistPath: Path): String? = block(plistPath)
        }

    private fun failingRunner(message: String): PlutilRunner =
        object : PlutilRunner() {
            override fun toJson(plistPath: Path): String? = error(message)
        }

    private fun stubParser(block: (String, Path) -> Browser?): PlistJsonParser =
        object : PlistJsonParser() {
            override fun parseBrowser(jsonText: String, applicationPath: Path): Browser? =
                block(jsonText, applicationPath)
        }

    private fun failingParser(message: String): PlistJsonParser =
        object : PlistJsonParser() {
            override fun parseBrowser(jsonText: String, applicationPath: Path): Browser? =
                error(message)
        }
}
