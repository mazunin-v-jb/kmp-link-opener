package dev.hackathon.linkopener.app.tray

import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TrayMenuTest {

    @Test
    fun `builder produces one JMenuItem per TrayMenuItem in order`() {
        val menu = buildTrayMenu(
            listOf(
                TrayMenuItem("Settings") {},
                TrayMenuItem("Quit") {},
            ),
        )

        assertEquals(2, menu.componentCount)
        assertEquals("Settings", (menu.getComponent(0) as JMenuItem).text)
        assertEquals("Quit", (menu.getComponent(1) as JMenuItem).text)
    }

    @Test
    fun `clicking a menu item invokes its onClick`() {
        var settingsCalls = 0
        var quitCalls = 0
        val menu = buildTrayMenu(
            listOf(
                TrayMenuItem("Settings") { settingsCalls++ },
                TrayMenuItem("Quit") { quitCalls++ },
            ),
        )

        // Fire ActionListeners directly — easier than synthesizing a real
        // mouse-driven event chain through Swing, and validates the wiring
        // we care about (label → handler).
        (menu.getComponent(0) as JMenuItem).doClick(0)
        assertEquals(1, settingsCalls)
        assertEquals(0, quitCalls)

        (menu.getComponent(1) as JMenuItem).doClick(0)
        assertEquals(1, quitCalls)
    }

    @Test
    fun `empty list produces an empty popup`() {
        val menu = buildTrayMenu(emptyList())
        assertEquals(0, menu.componentCount)
    }

    @Test
    fun `invoker frame stays hidden and non-focus-stealing after creation`() {
        val invoker = createTrayMenuInvoker()
        try {
            // Hidden so it doesn't show up as a phantom window in the user's
            // task switcher / Dock. Non-focusable-window-state so opening the
            // popup doesn't steal focus from the foreground app — the popup
            // appears at the cursor without making the user's text caret blink
            // away.
            assertFalse(invoker.isVisible)
            assertFalse(invoker.focusableWindowState)
            assertEquals(true, invoker.isUndecorated)
        } finally {
            invoker.dispose()
        }
    }
}
