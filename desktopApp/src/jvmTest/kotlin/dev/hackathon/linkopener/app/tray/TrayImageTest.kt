package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import dev.hackathon.linkopener.platform.HostOs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class TrayImageTest {

    private object DummyPainter : Painter() {
        override val intrinsicSize: Size = Size(64f, 64f)
        override fun DrawScope.onDraw() {
            drawCircle(Color.Black, radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f))
        }
    }

    private var savedTemplateProperty: String? = null

    @BeforeTest
    fun setUp() {
        // Stash the existing value so we can restore it — the test should not
        // leak its mutation into other tests in the same JVM.
        savedTemplateProperty = System.getProperty(MAC_OS_TEMPLATE_PROPERTY)
        System.clearProperty(MAC_OS_TEMPLATE_PROPERTY)
    }

    @AfterTest
    fun tearDown() {
        if (savedTemplateProperty != null) {
            System.setProperty(MAC_OS_TEMPLATE_PROPERTY, savedTemplateProperty!!)
        } else {
            System.clearProperty(MAC_OS_TEMPLATE_PROPERTY)
        }
    }

    @Test
    fun `enableMacOsTrayTemplateImages sets the documented JDK property`() {
        enableMacOsTrayTemplateImages()

        // Per JDK-8252015, the macOS CImage path reads this property when
        // building NSImages for tray icons; if it's "true", the icon is
        // installed as a template image and the system tints it monochrome.
        // No per-image hashtable trick is needed (and in fact doesn't work,
        // because CTrayIcon internally redraws the source into a fresh
        // BufferedImage — see the docstring on `enableMacOsTrayTemplateImages`).
        assertEquals("true", System.getProperty(MAC_OS_TEMPLATE_PROPERTY))
    }

    @Test
    fun `image is sized according to host platform`() {
        // Spot-check both retina-class and non-retina-class platforms — they
        // share the same code path so this guards the dispatch table from
        // drift. Pixel size = logical pt × hi-DPI factor (2): macOS 22pt → 44px,
        // Windows 16pt → 32px.
        val mac = prepareTrayImage(DummyPainter, HostOs.MacOs)
        val windows = prepareTrayImage(DummyPainter, HostOs.Windows)

        assertEquals(44, mac.width)
        assertEquals(44, mac.height)
        assertEquals(32, windows.width)
        assertEquals(32, windows.height)
    }

    @Test
    fun `each call returns a fresh BufferedImage`() {
        // Caching is intentionally left to the composable layer
        // (`remember(painter, host) { ... }`) — this helper must always return
        // a fresh BufferedImage so it can't be poisoned by a previous caller
        // mutating the returned instance.
        val first = prepareTrayImage(DummyPainter, HostOs.MacOs)
        val second = prepareTrayImage(DummyPainter, HostOs.MacOs)
        assertNotSame(first, second)
    }
}
