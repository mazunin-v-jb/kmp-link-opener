package dev.hackathon.linkopener.app.tray

import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrayMouseHandlerTest {

    private class Recorder {
        var leftClicks: Int = 0
        var popupTriggers: MutableList<Pair<Int, Int>> = mutableListOf()

        val handler = TrayMouseHandler(
            onLeftClick = { leftClicks++ },
            onPopupTrigger = { x, y -> popupTriggers += x to y },
        )
    }

    private fun event(
        button: Int,
        popupTrigger: Boolean,
        x: Int = 100,
        y: Int = 200,
    ): MouseEvent {
        // Any AWT Component works as a source — we only care that the event
        // carries the right `button` / popup-trigger flag and that
        // `xOnScreen` / `yOnScreen` are accessible. JLabel is cheap and
        // doesn't need a peer.
        val source: Component = JLabel()
        return MouseEvent(
            source,
            /* id = */ MouseEvent.MOUSE_PRESSED,
            /* when = */ 0L,
            /* modifiers = */ 0,
            /* x = */ x,
            /* y = */ y,
            /* xAbs = */ x,
            /* yAbs = */ y,
            /* clickCount = */ 1,
            /* popupTrigger = */ popupTrigger,
            /* button = */ button,
        )
    }

    @Test
    fun `plain left click fires onLeftClick`() {
        val r = Recorder()
        // Mouse-clicked is the canonical "completed left tap" event AWT emits
        // after a matching press+release on the same target.
        val click = event(button = MouseEvent.BUTTON1, popupTrigger = false)
        r.handler.mouseClicked(MouseEvent(
            click.component, MouseEvent.MOUSE_CLICKED, 0L, 0,
            click.x, click.y, click.xOnScreen, click.yOnScreen,
            1, false, MouseEvent.BUTTON1,
        ))

        assertEquals(1, r.leftClicks)
        assertTrue(r.popupTriggers.isEmpty(), "left click must not trigger the popup path")
    }

    @Test
    fun `popup-trigger on press fires onPopupTrigger with screen coords`() {
        val r = Recorder()
        // macOS reports popup-trigger on press; we honor it there.
        val pressed = event(button = MouseEvent.BUTTON3, popupTrigger = true, x = 510, y = 22)

        r.handler.mousePressed(pressed)

        assertEquals(0, r.leftClicks)
        assertEquals(listOf(510 to 22), r.popupTriggers)
    }

    @Test
    fun `popup-trigger on release fires onPopupTrigger`() {
        val r = Recorder()
        // Windows / Linux report popup-trigger on release. Same handler must
        // fire there.
        val released = event(button = MouseEvent.BUTTON3, popupTrigger = true, x = 17, y = 99)

        r.handler.mouseReleased(released)

        assertEquals(listOf(17 to 99), r.popupTriggers)
        assertEquals(0, r.leftClicks)
    }

    @Test
    fun `popup-trigger flagged click does not fire onLeftClick`() {
        val r = Recorder()
        // On macOS a Ctrl+left-click is reported as a popup trigger with
        // BUTTON1. The handler must route it to the popup, not Settings.
        val click = MouseEvent(
            JLabel(), MouseEvent.MOUSE_CLICKED, 0L, MouseEvent.CTRL_DOWN_MASK,
            10, 10, 10, 10,
            1, /* popupTrigger = */ true, MouseEvent.BUTTON1,
        )

        r.handler.mouseClicked(click)

        assertEquals(0, r.leftClicks)
    }

    @Test
    fun `non-left button without popup trigger is ignored on click`() {
        val r = Recorder()
        // Defensive: if a platform synthesizes a middle-button click without
        // a popup-trigger flag, we want it ignored, not opening Settings.
        val middleClick = MouseEvent(
            JLabel(), MouseEvent.MOUSE_CLICKED, 0L, 0,
            5, 5, 5, 5,
            1, false, MouseEvent.BUTTON2,
        )

        r.handler.mouseClicked(middleClick)

        assertEquals(0, r.leftClicks)
        assertTrue(r.popupTriggers.isEmpty())
    }

    @Test
    fun `non-popup release does nothing`() {
        val r = Recorder()
        // mouseReleased fires for every release, including the tail of a
        // normal left click. Without isPopupTrigger we must not show the menu.
        val release = event(button = MouseEvent.BUTTON1, popupTrigger = false)
        r.handler.mouseReleased(MouseEvent(
            release.component, MouseEvent.MOUSE_RELEASED, 0L, 0,
            release.x, release.y, release.xOnScreen, release.yOnScreen,
            1, false, MouseEvent.BUTTON1,
        ))

        assertEquals(0, r.leftClicks)
        assertTrue(r.popupTriggers.isEmpty())
    }

    @Test
    fun `non-popup press does nothing`() {
        val r = Recorder()
        val press = event(button = MouseEvent.BUTTON1, popupTrigger = false)
        r.handler.mousePressed(press)

        assertEquals(0, r.leftClicks)
        assertFalse(r.popupTriggers.isNotEmpty())
    }
}
