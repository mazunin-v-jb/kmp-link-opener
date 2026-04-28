package dev.hackathon.linkopener.platform.macos

import dev.hackathon.linkopener.core.model.Browser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacOsLinkLauncherTest {

    private val safari = Browser(
        bundleId = "com.apple.Safari",
        displayName = "Safari",
        applicationPath = "/Applications/Safari.app",
        version = "17.4",
    )

    @Test
    fun buildsExpectedOpenCommand() = runTest {
        val captured = mutableListOf<List<String>>()
        val launcher = MacOsLinkLauncher(processFactory = { args ->
            captured.add(args)
            FakeProcess(exitCode = 0)
        })

        launcher.openIn(safari, "https://example.com/page?q=1&r=2")

        assertEquals(
            listOf(
                listOf(
                    "open",
                    "-a",
                    "/Applications/Safari.app",
                    "--",
                    "https://example.com/page?q=1&r=2",
                ),
            ),
            captured,
        )
    }

    @Test
    fun returnsTrueWhenProcessExitsZero() = runTest {
        val launcher = MacOsLinkLauncher(processFactory = { FakeProcess(exitCode = 0) })

        assertTrue(launcher.openIn(safari, "https://example.com"))
    }

    @Test
    fun returnsFalseWhenProcessExitsNonZero() = runTest {
        val launcher = MacOsLinkLauncher(processFactory = { FakeProcess(exitCode = 1) })

        assertFalse(launcher.openIn(safari, "https://example.com"))
    }

    @Test
    fun returnsFalseWhenProcessFactoryThrows() = runTest {
        val launcher = MacOsLinkLauncher(processFactory = {
            throw RuntimeException("simulated exec failure")
        })

        assertFalse(launcher.openIn(safari, "https://example.com"))
    }

    @Test
    fun pathsWithSpacesAreForwardedAsSingleArgument() = runTest {
        val captured = mutableListOf<List<String>>()
        val launcher = MacOsLinkLauncher(processFactory = { args ->
            captured.add(args)
            FakeProcess(exitCode = 0)
        })
        val chrome = safari.copy(
            bundleId = "com.google.Chrome",
            displayName = "Google Chrome",
            applicationPath = "/Applications/Google Chrome.app",
        )

        launcher.openIn(chrome, "https://example.com")

        // Path with a space stays as one argv entry — ProcessBuilder doesn't
        // need shell quoting since each argument is passed literally.
        assertEquals("/Applications/Google Chrome.app", captured.single()[2])
    }

    @Test
    fun defaultProcessFactorySpawnsRealProcessOnMacOs() = runTest {
        // macOS-only: exercise the constructor's default `processFactory` so
        // the bytecode for that lambda is actually executed in coverage. We
        // hand it a non-existent .app — `open` prints "The file ... does not
        // exist" to stderr (visible because of inheritIO) and exits non-zero,
        // so launcher.openIn returns false. The point is just to invoke the
        // default factory without throwing.
        org.junit.Assume.assumeTrue("requires macOS host", System.getProperty("os.name").orEmpty().lowercase().let {
            "mac" in it || "darwin" in it
        })
        val launcher = MacOsLinkLauncher() // default processFactory
        val nonExistent = safari.copy(applicationPath = "/Applications/__nonexistent_for_test__.app")
        val launched = launcher.openIn(nonExistent, "https://example.com")
        assertFalse(launched)
    }

    @Test
    fun chromiumProfileSwitchesToArgsWithProfileDirectory() = runTest {
        // Stage 046: when the browser carries a profile and is Chromium-family,
        // `--args --profile-directory=<id>` must replace the bare `-- <url>`.
        val captured = mutableListOf<List<String>>()
        val launcher = MacOsLinkLauncher(processFactory = { args ->
            captured.add(args)
            FakeProcess(exitCode = 0)
        })
        val chrome = dev.hackathon.linkopener.core.model.Browser(
            bundleId = "com.google.Chrome",
            displayName = "Google Chrome",
            applicationPath = "/Applications/Google Chrome.app",
            version = "131.0",
            profile = dev.hackathon.linkopener.core.model.BrowserProfile(
                id = "Profile 1",
                displayName = "Work",
            ),
            family = dev.hackathon.linkopener.core.model.BrowserFamily.Chromium,
        )

        launcher.openIn(chrome, "https://example.com/?q=1")

        assertEquals(
            listOf(
                // -na (new instance) is required so Chrome processes URL +
                // profile flag together; without -n, an already-running
                // Chrome receives only the URL (via Apple Event) and the
                // profile flag separately, dropping the URL.
                "open", "-na", "/Applications/Google Chrome.app",
                "--args", "--profile-directory=Profile 1",
                "https://example.com/?q=1",
            ),
            captured.single(),
        )
    }

    @Test
    fun profileWithNonChromiumFamilyFallsBackToOpenA() = runTest {
        // Defensive: profile is set but family is Other (shouldn't happen
        // through real discovery, but if some manual flow ever produces this
        // shape we don't want to silently invoke unknown CLI flags on Safari).
        val captured = mutableListOf<List<String>>()
        val launcher = MacOsLinkLauncher(processFactory = { args ->
            captured.add(args)
            FakeProcess(exitCode = 0)
        })
        val odd = safari.copy(
            profile = dev.hackathon.linkopener.core.model.BrowserProfile("X", "Y"),
            family = dev.hackathon.linkopener.core.model.BrowserFamily.Other,
        )

        launcher.openIn(odd, "https://example.com")

        // Plain `open -a <path> -- <url>` form, no `--args`.
        assertEquals(
            listOf("open", "-a", safari.applicationPath, "--", "https://example.com"),
            captured.single(),
        )
    }

    @Test
    fun urlSpecialCharactersArePassedThroughVerbatim() = runTest {
        val captured = mutableListOf<List<String>>()
        val launcher = MacOsLinkLauncher(processFactory = { args ->
            captured.add(args)
            FakeProcess(exitCode = 0)
        })
        val tricky = "https://example.com/?q=hello world&fragment=#anchor"

        launcher.openIn(safari, tricky)

        // The url is the last positional, after `--`. Special chars must
        // survive untouched (no shell interpretation, no escaping).
        assertEquals(tricky, captured.single().last())
    }

    private class FakeProcess(private val exitCode: Int) : Process() {
        override fun getOutputStream() = error("not used")
        override fun getInputStream() = error("not used")
        override fun getErrorStream() = error("not used")
        override fun waitFor(): Int = exitCode
        override fun exitValue(): Int = exitCode
        override fun destroy() = Unit
        override fun isAlive(): Boolean = false
    }
}
