package dev.hackathon.linkopener.app.tray

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * Routes raw AWT [MouseEvent]s on the tray icon to the right callback so the
 * app can override the default platform behavior:
 *
 * - **Left click without popup-trigger** → [onLeftClick] (we open Settings).
 * - **Popup-trigger event** ([MouseEvent.isPopupTrigger]) → [onPopupTrigger]
 *   with the screen-absolute coordinates so the caller can show a JPopupMenu
 *   at the cursor.
 *
 * macOS reports popup triggers on `mousePressed`; Windows/Linux on
 * `mouseReleased`. Listening to both — combined with the `isPopupTrigger` check
 * — handles all three platforms uniformly. The left-click callback fires from
 * `mouseClicked` (after press+release on the same target) so it doesn't
 * compete with a brewing popup gesture.
 */
internal class TrayMouseHandler(
    private val onLeftClick: () -> Unit,
    private val onPopupTrigger: (screenX: Int, screenY: Int) -> Unit,
) : MouseAdapter() {

    override fun mousePressed(e: MouseEvent) {
        if (e.isPopupTrigger) onPopupTrigger(e.xOnScreen, e.yOnScreen)
    }

    override fun mouseReleased(e: MouseEvent) {
        if (e.isPopupTrigger) onPopupTrigger(e.xOnScreen, e.yOnScreen)
    }

    override fun mouseClicked(e: MouseEvent) {
        if (e.isPopupTrigger) return
        if (e.button == MouseEvent.BUTTON1) onLeftClick()
    }
}
