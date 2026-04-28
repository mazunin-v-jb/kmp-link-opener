package dev.hackathon.linkopener.platform.macos

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class MacOsBrowserDiscoverySmokeTest {

    @Test
    fun discoversAtLeastOneBrowserOnMacOs() {
        val osName = System.getProperty("os.name").orEmpty()
        assumeTrue("Smoke test runs only on macOS, current: $osName", osName.lowercase().contains("mac"))

        val browsers = runBlocking { MacOsBrowserDiscovery().discover() }
        println("Discovered ${browsers.size} browser(s):")
        browsers.forEach { println("  - ${it.displayName} ${it.version} (${it.bundleId}) at ${it.applicationPath}") }

        assertTrue(browsers.isNotEmpty(), "expected at least one browser on macOS")
        assertTrue(
            browsers.any { it.bundleId == "com.apple.Safari" },
            "expected Safari to be present (bundle ids: ${browsers.map { it.bundleId }})",
        )
    }
}
