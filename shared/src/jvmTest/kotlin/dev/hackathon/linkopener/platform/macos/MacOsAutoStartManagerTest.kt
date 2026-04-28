package dev.hackathon.linkopener.platform.macos

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MacOsAutoStartManagerTest {

    private lateinit var tmpDir: Path

    @BeforeTest
    fun setUp() {
        tmpDir = createTempDirectory(prefix = "macos-autostart-test-")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun isEnabledIsFalseInitially() = runTest {
        val manager = newManager()
        assertFalse(manager.isEnabled())
    }

    @Test
    fun setEnabledTrueCreatesPlistWithLabelAndExecutable() = runTest {
        val manager = newManager(executable = "/fake/path/to/java")

        manager.setEnabled(true)

        val plist = tmpDir.resolve("dev.hackathon.linkopener.test.plist")
        assertTrue(Files.exists(plist), "plist should be written")
        val content = Files.readString(plist)
        assertTrue(content.contains("<key>Label</key>"))
        assertTrue(content.contains("<string>dev.hackathon.linkopener.test</string>"))
        assertTrue(content.contains("<string>/fake/path/to/java</string>"))
        assertTrue(content.contains("<key>RunAtLoad</key>"))
        assertTrue(manager.isEnabled())
    }

    @Test
    fun setEnabledFalseRemovesPlist() = runTest {
        val manager = newManager()
        manager.setEnabled(true)
        assertTrue(manager.isEnabled())

        manager.setEnabled(false)

        assertFalse(manager.isEnabled())
        assertEquals(0, Files.list(tmpDir).use { it.count() })
    }

    @Test
    fun setEnabledTrueIsIdempotent() = runTest {
        val manager = newManager()

        manager.setEnabled(true)
        manager.setEnabled(true)

        assertTrue(manager.isEnabled())
    }

    @Test
    fun createsLaunchAgentDirIfMissing() = runTest {
        val nested = tmpDir.resolve("nested/agents")
        val manager = MacOsAutoStartManager(
            launchAgentDir = nested,
            label = "dev.hackathon.linkopener.test",
            executableLocator = { "/fake/java" },
        )

        manager.setEnabled(true)

        assertTrue(Files.exists(nested.resolve("dev.hackathon.linkopener.test.plist")))
    }

    @Test
    fun resolvePackagedExecutableFindsLauncherInsideAppBundle() {
        // jpackage layout: <bundle>.app/Contents/runtime/Contents/Home
        val appBundle = tmpDir.resolve("Link Opener.app")
        val javaHome = appBundle.resolve("Contents/runtime/Contents/Home")
        val macosDir = appBundle.resolve("Contents/MacOS")
        Files.createDirectories(javaHome)
        Files.createDirectories(macosDir)
        val executable = macosDir.resolve("Link Opener")
        Files.writeString(executable, "#!/bin/sh\necho hi\n")

        val resolved = MacOsAutoStartManager.resolvePackagedExecutable(javaHome)

        assertEquals(executable, resolved)
    }

    @Test
    fun resolvePackagedExecutableReturnsNullOutsideBundle() {
        // A dev JDK install at e.g. ~/Library/Java/.../Contents/Home — no .app
        // anywhere in the parent chain.
        val javaHome = tmpDir.resolve("jbr/Contents/Home")
        Files.createDirectories(javaHome)

        assertEquals(null, MacOsAutoStartManager.resolvePackagedExecutable(javaHome))
    }

    @Test
    fun resolvePackagedExecutableReturnsNullWhenMacOsDirIsEmpty() {
        val appBundle = tmpDir.resolve("Empty.app")
        val javaHome = appBundle.resolve("Contents/runtime/Contents/Home")
        Files.createDirectories(javaHome)
        Files.createDirectories(appBundle.resolve("Contents/MacOS"))
        // No file inside Contents/MacOS — resolver should bail out.

        assertEquals(null, MacOsAutoStartManager.resolvePackagedExecutable(javaHome))
    }

    @Test
    fun resolvePackagedExecutableReturnsNullWhenMacOsDirMissing() {
        val appBundle = tmpDir.resolve("Broken.app")
        val javaHome = appBundle.resolve("Contents/runtime/Contents/Home")
        Files.createDirectories(javaHome)
        // No Contents/MacOS dir at all.

        assertEquals(null, MacOsAutoStartManager.resolvePackagedExecutable(javaHome))
    }

    private fun newManager(executable: String = "/usr/bin/java"): MacOsAutoStartManager =
        MacOsAutoStartManager(
            launchAgentDir = tmpDir,
            label = "dev.hackathon.linkopener.test",
            executableLocator = { executable },
        )
}
