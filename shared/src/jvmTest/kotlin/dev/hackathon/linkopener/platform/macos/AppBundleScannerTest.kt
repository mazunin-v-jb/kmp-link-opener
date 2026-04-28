package dev.hackathon.linkopener.platform.macos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppBundleScannerTest {

    @Test
    fun findsTopLevelAppBundles() {
        val root = Files.createTempDirectory("scanner-test")
        try {
            createApp(root.resolve("Safari.app"))
            createApp(root.resolve("Google Chrome.app"))
            root.resolve("readme.txt").writeText("ignore me")

            val results = AppBundleScanner().findAppBundles(root).map { it.fileName.toString() }

            assertEquals(setOf("Safari.app", "Google Chrome.app"), results.toSet())
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun findsAppsOneLevelDeep() {
        val root = Files.createTempDirectory("scanner-test")
        try {
            val utilities = root.resolve("Utilities").also { it.createDirectory() }
            createApp(utilities.resolve("Console.app"))

            val results = AppBundleScanner().findAppBundles(root).map { it.fileName.toString() }

            assertTrue(results.contains("Console.app"), "expected Console.app in $results")
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun doesNotDescendIntoAppBundles() {
        val root = Files.createTempDirectory("scanner-test")
        try {
            val outerApp = root.resolve("Outer.app").also { createApp(it) }
            createApp(outerApp.resolve("Inner.app"))

            val results = AppBundleScanner().findAppBundles(root).map { it.fileName.toString() }

            assertEquals(listOf("Outer.app"), results)
        } finally {
            cleanup(root)
        }
    }

    @Test
    fun returnsEmptyForMissingDirectory() {
        val results = AppBundleScanner().findAppBundles(Path("/definitely/not/here/$UNIQUE"))

        assertEquals(emptyList(), results)
    }

    @Test
    fun returnsEmptyForFilePath() {
        val tempFile = Files.createTempFile("scanner-test", ".txt")
        try {
            val results = AppBundleScanner().findAppBundles(tempFile)
            assertEquals(emptyList(), results)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun createApp(path: Path) {
        Files.createDirectories(path.resolve("Contents"))
        path.resolve("Contents").resolve("Info.plist").writeText("<plist/>")
    }

    private fun cleanup(root: Path) {
        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
        }
    }

    private companion object {
        const val UNIQUE = "kmp-link-opener-fake-path-2024"
    }
}
