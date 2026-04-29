package dev.hackathon.linkopener.platform.macos

import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MacOsBrowserIconLoaderTest {

    private val isMacOs: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    @Test
    fun `Safari icon is extracted via plutil + sips and yields valid PNG bytes`() = runTest {
        // Safari ships with macOS, so the test has a stable fixture across
        // any macOS host without bundling assets. Skip on non-mac runners
        // — both `plutil` and `sips` are macOS-only binaries.
        assumeTrue("Safari + plutil/sips only exist on macOS", isMacOs)

        val loader = MacOsBrowserIconLoader()
        val bytes = loader.load("/Applications/Safari.app")

        assertNotNull(bytes, "Safari.app should yield a real icon, not the folder one we got from FileSystemView")
        // Non-trivial payload: a real Safari icon at 128px is ~10 KB+. A sub-100B
        // result almost certainly means sips fell back to a degenerate path.
        assertTrue(bytes.size > 1024, "PNG payload too small to be a real icon: ${bytes.size} bytes")
        // PNG magic: 89 50 4E 47 — guards against sips emitting a different
        // format if `--format png` ever changes default behavior.
        assertTrue(
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte(),
            "first 8 bytes should be the PNG magic signature",
        )
    }

    @Test
    fun `nonexistent application path returns null without throwing`() = runTest {
        // No mac assumption needed — the loader's first guard is
        // `Files.exists(Info.plist)` which short-circuits on every host.
        val loader = MacOsBrowserIconLoader()
        assertNull(loader.load("/Applications/__nonexistent_for_test__.app"))
    }
}
