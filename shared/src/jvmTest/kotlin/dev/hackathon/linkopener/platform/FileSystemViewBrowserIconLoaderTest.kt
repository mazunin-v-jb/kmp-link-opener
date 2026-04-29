package dev.hackathon.linkopener.platform

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FileSystemViewBrowserIconLoaderTest {

    private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    @Test
    fun `produces non-empty PNG bytes for an existing system path`() = runTest {
        // We only use this loader for Windows in production (macOS goes
        // through `MacOsBrowserIconLoader` because `.app` bundles look like
        // folders to `FileSystemView`). For test purposes any path that
        // resolves to *some* OS-rendered icon is enough — Safari serves as a
        // cross-platform-ish stand-in on macOS hosts (it returns the folder
        // icon, but it's still a valid PNG payload that proves the
        // rasterization pipeline works end-to-end).
        assumeTrue("This smoke test relies on a guaranteed-installed fixture, currently macOS-only", isMacOs)

        val loader = FileSystemViewBrowserIconLoader()
        val bytes = loader.load("/Applications/Safari.app")

        assertNotNull(bytes, "FileSystemView should produce *some* icon for an existing path")
        assertTrue(bytes.size > 64, "PNG byte payload looks suspiciously small: ${bytes.size}")
        assertTrue(
            bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
                && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "first 8 bytes should be the PNG magic signature",
        )
    }

    @Test
    fun `nonexistent path returns null without throwing`() = runTest {
        val loader = FileSystemViewBrowserIconLoader()
        val bytes = loader.load("/Applications/__nonexistent_for_test__.app")
        assertNull(bytes)
    }
}
