package dev.hackathon.linkopener.app.tray

import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.MenuSelectionManager

/**
 * One row in the tray context menu. [onClick] is dispatched on the AWT EDT.
 */
internal data class TrayMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Builds a [JPopupMenu] from [items]. Each entry becomes a [JMenuItem]; the
 * onClick handler runs on whatever thread Swing dispatches to (EDT).
 */
internal fun buildTrayMenu(items: List<TrayMenuItem>): JPopupMenu {
    val menu = JPopupMenu()
    for (item in items) {
        val swingItem = JMenuItem(item.label)
        swingItem.addActionListener { item.onClick() }
        menu.add(swingItem)
    }
    return menu
}

/**
 * Shows [menu] at screen-absolute coordinates [screenX]/[screenY].
 *
 * `JPopupMenu.show(invoker, x, y)` interprets coordinates as invoker-relative,
 * so we move an invisible [invoker] frame to the requested screen position
 * and pass `(0, 0)`. The frame is offscreen-undecorated and toggled visible
 * each time the popup opens so its `WindowFocusListener` fires reliably.
 *
 * Outside-click dismiss is wired via that listener (see
 * [createTrayMenuInvoker]) — the original "default Swing dismiss" assumption
 * doesn't hold here because the popup's heavyweight window is parented under
 * a hidden offscreen invoker, and the Wayland XGrabPointer path is broken
 * anyway (JDK-8307529).
 */
internal fun showTrayMenuAt(
    menu: JPopupMenu,
    invoker: JFrame,
    screenX: Int,
    screenY: Int,
) {
    invoker.setLocation(screenX, screenY)
    invoker.isVisible = true
    menu.show(invoker, 0, 0)
}

/**
 * Creates the invisible Swing frame used as the popup's invoker. Positioned
 * offscreen with zero size and no decorations.
 *
 * Stays focusable on purpose — when the popup opens we toggle
 * `setVisible(true)` so the invoker requests focus, the heavyweight popup
 * inherits it, and clicks outside trigger `windowLostFocus` on the invoker
 * (canonical "Sylvain Bugat" tray-popup recipe). The listener clears the
 * menu selection (which dismisses the popup) and hides the invoker again so
 * it's primed for the next open.
 *
 * `focusableWindowState = false` would defeat this — without focus the
 * invoker can never lose it, and the popup gets stuck open until an item is
 * picked. That was Stage 02's bug.
 */
internal fun createTrayMenuInvoker(): JFrame = JFrame().apply {
    isUndecorated = true
    type = java.awt.Window.Type.UTILITY
    setSize(1, 1)
    setLocation(-10_000, -10_000)
    addWindowFocusListener(object : WindowFocusListener {
        override fun windowGainedFocus(e: WindowEvent) = Unit
        override fun windowLostFocus(e: WindowEvent) {
            MenuSelectionManager.defaultManager().clearSelectedPath()
            this@apply.isVisible = false
        }
    })
}
