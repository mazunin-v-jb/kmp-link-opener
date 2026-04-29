package dev.hackathon.linkopener.platform.linux

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class LinuxBrowserIconLoaderTest {

    private lateinit var root: Path
    private lateinit var dataDir: Path
    private lateinit var homeIcons: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("linux-icon-loader-test")
        dataDir = root.resolve("data").also { it.createDirectories() }
        homeIcons = root.resolve("home-icons").also { it.createDirectories() }
    }

    @AfterTest
    fun tearDown() {
        runCatching { Files.walk(root).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun makeLoader() = LinuxBrowserIconLoader(
        xdgDataDirs = listOf(dataDir.toString()),
        homeIconsDir = homeIcons.toString(),
    )

    @Test
    fun `absolute Icon path is read directly without theme walk`() = runTest {
        // The freedesktop spec lets `Icon=` be an absolute path; we must
        // honor that and not waste time walking theme dirs.
        val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)  // PNG magic
        val absoluteIcon = root.resolve("vendor-firefox.png")
        absoluteIcon.writeBytes(iconBytes)

        val desktop = root.resolve("firefox.desktop")
        desktop.writeText(
            """
            [Desktop Entry]
            Name=Firefox
            Icon=${absoluteIcon}
            Exec=firefox %u
            """.trimIndent(),
        )

        val result = makeLoader().load(desktop.toString())
        assertContentEquals(iconBytes, result)
    }

    @Test
    fun `Icon name is resolved against XDG_DATA_DIRS subtree`() = runTest {
        // Real distros nest icons like
        // /usr/share/icons/hicolor/48x48/apps/firefox.png — we walk up to
        // two nested levels deep below the search root.
        val themedDir = dataDir.resolve("icons/hicolor/128x128/apps")
        themedDir.createDirectories()
        val iconBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        themedDir.resolve("brave-browser.png").writeBytes(iconBytes)

        val desktop = root.resolve("brave-browser.desktop")
        desktop.writeText(
            """
            [Desktop Entry]
            Name=Brave
            Icon=brave-browser
            Exec=brave %u
            """.trimIndent(),
        )

        val result = makeLoader().load(desktop.toString())
        assertContentEquals(iconBytes, result)
    }

    @Test
    fun `home icons dir wins over XDG dirs (matches spec lookup order)`() = runTest {
        val homeBytes = byteArrayOf(1, 2, 3)
        val xdgBytes = byteArrayOf(9, 9, 9)

        // Both paths exist; the loader should pick the one in $HOME/.icons
        // because the spec orders user-private themes above system ones.
        homeIcons.resolve("hicolor/scalable/apps").createDirectories()
        homeIcons.resolve("hicolor/scalable/apps/chrome.png").writeBytes(homeBytes)

        val xdgDir = dataDir.resolve("icons/hicolor/scalable/apps")
        xdgDir.createDirectories()
        xdgDir.resolve("chrome.png").writeBytes(xdgBytes)

        val desktop = root.resolve("chrome.desktop")
        desktop.writeText("[Desktop Entry]\nIcon=chrome\n")

        assertContentEquals(homeBytes, makeLoader().load(desktop.toString()))
    }

    @Test
    fun `SVG fallback is used when PNG is missing`() = runTest {
        // Pure-vector themes (e.g. Papirus) ship .svg only. We probe png →
        // svg → xpm so vector themes still resolve.
        val svgBytes = "<svg/>".toByteArray()
        val themedDir = dataDir.resolve("icons/hicolor/scalable/apps")
        themedDir.createDirectories()
        themedDir.resolve("vivaldi.svg").writeBytes(svgBytes)

        val desktop = root.resolve("vivaldi.desktop")
        desktop.writeText("[Desktop Entry]\nIcon=vivaldi\n")

        assertContentEquals(svgBytes, makeLoader().load(desktop.toString()))
    }

    @Test
    fun `Icon key from Desktop Action sub-group is ignored`() = runTest {
        // .desktop files can carry per-action icons in `[Desktop Action *]`
        // sub-groups; we want the main `[Desktop Entry]` icon, not the first
        // `Icon=` line we encounter.
        dataDir.resolve("icons/hicolor/scalable/apps").createDirectories()
        val mainBytes = byteArrayOf(7, 7, 7)
        val newWindowBytes = byteArrayOf(0, 0, 0)
        dataDir.resolve("icons/hicolor/scalable/apps/main-icon.png").writeBytes(mainBytes)
        dataDir.resolve("icons/hicolor/scalable/apps/new-window-icon.png").writeBytes(newWindowBytes)

        val desktop = root.resolve("complex.desktop")
        desktop.writeText(
            """
            [Desktop Action NewWindow]
            Icon=new-window-icon

            [Desktop Entry]
            Name=Complex
            Icon=main-icon
            """.trimIndent(),
        )

        assertContentEquals(mainBytes, makeLoader().load(desktop.toString()))
    }

    @Test
    fun `missing desktop file returns null`() = runTest {
        assertNull(makeLoader().load(root.resolve("does-not-exist.desktop").toString()))
    }

    @Test
    fun `desktop file without Icon key returns null`() = runTest {
        val desktop = root.resolve("no-icon.desktop")
        desktop.writeText("[Desktop Entry]\nName=Foo\n")
        assertNull(makeLoader().load(desktop.toString()))
    }

    @Test
    fun `icon name with no matching file in any search dir returns null`() = runTest {
        val desktop = root.resolve("ghost.desktop")
        desktop.writeText("[Desktop Entry]\nIcon=ghost-browser\n")
        assertNull(makeLoader().load(desktop.toString()))
    }
}
