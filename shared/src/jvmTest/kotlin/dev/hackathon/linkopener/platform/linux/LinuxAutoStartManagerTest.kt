package dev.hackathon.linkopener.platform.linux

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxAutoStartManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun setEnabledTrueWritesDesktopFile() = runTest {
        val dir = tmp.newFolder("autostart").toPath()
        val manager = LinuxAutoStartManager(
            autostartDir = dir,
            launchTokensProvider = { listOf("/usr/bin/java", "-jar", "/opt/link-opener.jar") },
        )

        assertFalse(manager.isEnabled())
        manager.setEnabled(true)
        assertTrue(manager.isEnabled())

        val body = Files.readString(dir.resolve("link-opener.desktop"))
        assertTrue(body.contains("Type=Application"))
        assertTrue(body.contains("Name=Link Opener"))
        assertTrue(body.contains("Exec=/usr/bin/java -jar /opt/link-opener.jar"))
        assertTrue(body.contains("X-GNOME-Autostart-enabled=true"))
    }

    @Test
    fun setEnabledFalseDeletesDesktopFile() = runTest {
        val dir = tmp.newFolder("autostart").toPath()
        val manager = LinuxAutoStartManager(
            autostartDir = dir,
            launchTokensProvider = { listOf("/usr/bin/link-opener") },
        )

        manager.setEnabled(true)
        assertTrue(manager.isEnabled())

        manager.setEnabled(false)
        assertFalse(manager.isEnabled())
    }

    @Test
    fun roundTripIsIdempotent() = runTest {
        val dir = tmp.newFolder("autostart").toPath()
        val manager = LinuxAutoStartManager(
            autostartDir = dir,
            launchTokensProvider = { listOf("/usr/bin/link-opener") },
        )
        manager.setEnabled(true)
        manager.setEnabled(true) // overwrite same file
        assertTrue(manager.isEnabled())
        manager.setEnabled(false)
        manager.setEnabled(false) // delete-already-deleted ok
        assertFalse(manager.isEnabled())
    }

    @Test
    fun missingLaunchTokensSkipsWriteSilently() = runTest {
        val dir = tmp.newFolder("autostart").toPath()
        val manager = LinuxAutoStartManager(
            autostartDir = dir,
            launchTokensProvider = { null },
        )
        manager.setEnabled(true)
        assertFalse(manager.isEnabled())
    }

    @Test
    fun spacesInPathsArePreserved() = runTest {
        val dir = tmp.newFolder("autostart").toPath()
        val manager = LinuxAutoStartManager(
            autostartDir = dir,
            launchTokensProvider = {
                listOf("/usr/bin/java", "-jar", "/home/user/My Apps/link-opener.jar")
            },
        )
        manager.setEnabled(true)
        val body = Files.readString(dir.resolve("link-opener.desktop"))
        // Path with space wrapped in double quotes per LinuxLaunchCommand.
        assertEquals(true, body.contains("\"/home/user/My Apps/link-opener.jar\""))
    }
}
