package dev.hackathon.linkopener.platform

import dev.hackathon.linkopener.core.model.Browser
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PrintingLinkLauncherTest {

    @Test
    fun openInPrintsBrowserAndUrlAndReturnsTrue() = runTest {
        val captured = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(captured))
        try {
            val launched = PrintingLinkLauncher().openIn(
                browser = Browser(
                    bundleId = "com.example.fake",
                    displayName = "Fake Browser",
                    applicationPath = "/Applications/Fake.app",
                    version = "1.0",
                ),
                url = "https://example.com",
            )

            assertTrue(launched)
        } finally {
            System.setOut(original)
        }
        val output = captured.toString()
        assertContains(output, "https://example.com")
        assertContains(output, "Fake Browser")
        assertContains(output, "/Applications/Fake.app")
    }
}
