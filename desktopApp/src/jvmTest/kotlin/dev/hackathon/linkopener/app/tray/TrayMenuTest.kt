package dev.hackathon.linkopener.app.tray

import java.awt.GraphicsEnvironment
import javax.swing.JMenuItem
import org.junit.Assume.assumeFalse
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
    fun `invoker frame is hidden, undecorated, and wires a focus listener for dismiss`() {
        // Constructing JFrame on a headless JVM (typical CI runner without
        // an X display) throws HeadlessException. Guard like the macOS
        // smoke tests in :shared do — the assertion exercises real AWT
        // behaviour, so there's no good headless substitute.
        assumeFalse(
            "Skipping JFrame test under a headless JVM (CI runner without display).",
            GraphicsEnvironment.isHeadless(),
        )
        val invoker = createTrayMenuInvoker()
        try {
            assertFalse(invoker.isVisible)
            assertEquals(true, invoker.isUndecorated)
            // Outside-click dismiss is implemented via a WindowFocusListener
            // (see TrayMenu.createTrayMenuInvoker) — invoker must stay
            // focusable so it can lose focus to the user's next click. The
            // listener wiring is the dismiss contract; assert it exists.
            assertEquals(true, invoker.focusableWindowState)
            assertEquals(1, invoker.windowFocusListeners.size)
        } finally {
            invoker.dispose()
        }
    }
}
