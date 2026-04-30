package dev.hackathon.linkopener.platform.linux

import dev.hackathon.linkopener.platform.linux.LinuxDefaultBrowserService.ProcessOutput
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxDefaultBrowserServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fakeXdgSettingsBinary(): File =
        File(tmp.newFolder("bin"), "xdg-settings").apply { createNewFile() }

    @Test
    fun isDefaultBrowserReadsXdgSettingsCheckYes() = runTest {
        var capturedArgs: List<String>? = null
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            pathLookup = { name ->
                if (name == "xdg-settings") fakeXdgSettingsBinary() else null
            },
            processRunner = { args ->
                capturedArgs = args
                ProcessOutput(exitCode = 0, stdout = "yes\n")
            },
        )
        assertTrue(service.isDefaultBrowser())
        assertEquals(
            listOf("xdg-settings", "check", "default-web-browser", "link-opener.desktop"),
            capturedArgs,
        )
    }

    @Test
    fun isDefaultBrowserReadsXdgSettingsCheckNo() = runTest {
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            pathLookup = { name -> if (name == "xdg-settings") fakeXdgSettingsBinary() else null },
            processRunner = { ProcessOutput(exitCode = 0, stdout = "no\n") },
        )
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun fallsBackToMimeAppsListWhenXdgSettingsMissing() = runTest {
        val configDir = tmp.newFolder("config")
        val mimeAppsList = File(configDir, "mimeapps.list").apply {
            writeText(
                """
                [Default Applications]
                x-scheme-handler/http=link-opener.desktop
                x-scheme-handler/https=link-opener.desktop
                """.trimIndent(),
            )
        }

        val service = LinuxDefaultBrowserService(
            mimeAppsList = mimeAppsList.toPath(),
            pathLookup = { null },
            processRunner = { error("xdg-settings should not be invoked when missing") },
        )
        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun mimeAppsListFallbackHonorsFirstHandlerInList() = runTest {
        val configDir = tmp.newFolder("config")
        val mimeAppsList = File(configDir, "mimeapps.list").apply {
            // Multiple handlers, ours is first; xdg-mime accepts these
            // semicolon-separated.
            writeText(
                """
                [Default Applications]
                x-scheme-handler/http=link-opener.desktop;firefox.desktop;
                """.trimIndent(),
            )
        }
        val service = LinuxDefaultBrowserService(
            mimeAppsList = mimeAppsList.toPath(),
            pathLookup = { null },
            processRunner = { error("not invoked") },
        )
        assertTrue(service.isDefaultBrowser())
    }

    @Test
    fun mimeAppsListFallbackReportsFalseWhenFirstHandlerIsAnotherBrowser() = runTest {
        val configDir = tmp.newFolder("config")
        val mimeAppsList = File(configDir, "mimeapps.list").apply {
            writeText(
                """
                [Default Applications]
                x-scheme-handler/http=firefox.desktop
                """.trimIndent(),
            )
        }
        val service = LinuxDefaultBrowserService(
            mimeAppsList = mimeAppsList.toPath(),
            pathLookup = { null },
            processRunner = { error("not invoked") },
        )
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun isDefaultBrowserReturnsFalseWhenNothingAvailable() = runTest {
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            pathLookup = { null },
            processRunner = { error("not invoked") },
        )
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun canOpenSystemSettingsTrueWhenCinnamonAndCommandPresent() {
        val cinnamonBin = File(tmp.newFolder("bin"), "cinnamon-settings").apply { createNewFile() }
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            xdgCurrentDesktop = { "X-Cinnamon" },
            pathLookup = { name -> if (name == "cinnamon-settings") cinnamonBin else null },
            processRunner = { error("not invoked") },
        )
        assertTrue(service.canOpenSystemSettings)
    }

    @Test
    fun canOpenSystemSettingsFalseOnUnknownDe() {
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            xdgCurrentDesktop = { "Sway" }, // not in the dispatch table
            pathLookup = { File("/usr/bin/some-binary") },
            processRunner = { error("not invoked") },
        )
        assertFalse(service.canOpenSystemSettings)
    }

    @Test
    fun openSystemSettingsSpawnsResolvedCommand() = runTest {
        val gnomeBin =
            File(tmp.newFolder("bin"), "gnome-control-center").apply { createNewFile() }
        var spawnedArgs: List<String>? = null
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            xdgCurrentDesktop = { "GNOME" },
            pathLookup = { name -> if (name == "gnome-control-center") gnomeBin else null },
            processRunner = { error("not invoked") },
            processSpawner = { args ->
                spawnedArgs = args
                FakeAliveProcess()
            },
        )
        assertTrue(service.openSystemSettings())
        assertEquals(
            listOf("gnome-control-center", "default-applications"),
            spawnedArgs,
        )
    }

    @Test
    fun openSystemSettingsFalseWhenCommandUnresolvable() = runTest {
        val service = LinuxDefaultBrowserService(
            mimeAppsList = tmp.newFolder("config").toPath().resolve("mimeapps.list"),
            xdgCurrentDesktop = { "Unknown" },
            pathLookup = { null },
            processRunner = { error("not invoked") },
            processSpawner = { error("not invoked") },
        )
        assertFalse(service.openSystemSettings())
    }

    private class FakeAliveProcess : Process() {
        override fun getOutputStream() = error("not used")
        override fun getInputStream() = error("not used")
        override fun getErrorStream() = error("not used")
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun destroy() = Unit
        override fun isAlive() = true
    }
}
