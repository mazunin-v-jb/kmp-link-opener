package dev.hackathon.linkopener.app.tray

import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

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
 * and pass `(0, 0)`. The frame is offscreen-undecorated and stays hidden — it
 * exists purely as a Swing parent so the popup gets the right focus / heavy-
 * weight treatment from the LookAndFeel.
 *
 * The popup dismisses itself when focus moves elsewhere (Swing default), so
 * clicking outside the menu collapses it without extra wiring.
 */
internal fun showTrayMenuAt(
    menu: JPopupMenu,
    invoker: JFrame,
    screenX: Int,
    screenY: Int,
) {
    invoker.setLocation(screenX, screenY)
    if (!invoker.isVisible) invoker.isVisible = true
    menu.show(invoker, 0, 0)
}

/**
 * Creates the invisible Swing frame used as the popup's invoker. Positioned
 * offscreen with zero size and no decorations, kept hidden until needed by
 * [showTrayMenuAt] (which calls `setVisible(true)` once and reuses).
 */
internal fun createTrayMenuInvoker(): JFrame = JFrame().apply {
    isUndecorated = true
    type = java.awt.Window.Type.UTILITY
    setSize(1, 1)
    setLocation(-10_000, -10_000)
    focusableWindowState = false
}
