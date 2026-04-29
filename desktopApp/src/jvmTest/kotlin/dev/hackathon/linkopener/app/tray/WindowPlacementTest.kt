package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowPlacementTest {

    @Test
    fun `center on a single primary monitor places window at midpoint`() {
        val screen = Rectangle(0, 0, 1920, 1080)
        val pos = centerInBounds(screen, windowWidth = 960.dp, windowHeight = 640.dp)

        // Top-left = ((1920 - 960) / 2, (1080 - 640) / 2) = (480, 220).
        // Sanity-check both axes against the formula so a sign-flip in the
        // future doesn't quietly land the window off-screen.
        assertEquals(WindowPosition(x = 480.dp, y = 220.dp), pos)
    }

    @Test
    fun `center on a secondary monitor offsets by the screen origin`() {
        // Secondary monitor sitting to the right of the primary (typical
        // dual-display setup): its top-left is at (1920, 0). Centering
        // there must add the screen's `x` offset, otherwise the window
        // would land on the primary monitor instead.
        val screen = Rectangle(1920, 0, 1920, 1080)
        val pos = centerInBounds(screen, windowWidth = 960.dp, windowHeight = 640.dp)
        assertEquals(WindowPosition(x = (1920 + 480).dp, y = 220.dp), pos)
    }

    @Test
    fun `picks the monitor whose bounds contain the cursor`() {
        val primary = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(1920, 0, 1920, 1080)

        // Cursor on primary → returns primary bounds.
        assertEquals(
            primary,
            pickScreenBoundsForCursor(listOf(primary, secondary), primary, Point(500, 500)),
        )
        // Cursor on secondary → returns secondary, despite primary being
        // listed first.
        assertEquals(
            secondary,
            pickScreenBoundsForCursor(listOf(primary, secondary), primary, Point(2500, 500)),
        )
    }

    @Test
    fun `falls back to provided primary when cursor lies outside all displays`() {
        // Happens during display reconfigure / locked session — the
        // PointerInfo cursor coordinates can briefly point at a defunct
        // monitor or a "between monitors" gap. We must still produce
        // something sane, not throw.
        val primary = Rectangle(0, 0, 1920, 1080)
        val secondary = Rectangle(1920, 0, 1920, 1080)
        val cursor = Point(-9999, -9999)

        assertEquals(
            primary,
            pickScreenBoundsForCursor(listOf(primary, secondary), primary, cursor),
        )
    }

    @Test
    fun `center is bounds-relative even when the screen origin is negative`() {
        // macOS layouts where a secondary monitor is positioned *above* or
        // *left of* the primary produce negative bounds origins. The
        // formula must still center on that screen (negative top-left is a
        // valid WindowPosition).
        val screen = Rectangle(-1440, -900, 1440, 900)
        val pos = centerInBounds(screen, windowWidth = 800.dp, windowHeight = 600.dp)
        assertEquals(WindowPosition(x = (-1440 + 320).dp, y = (-900 + 150).dp), pos)
    }
}
