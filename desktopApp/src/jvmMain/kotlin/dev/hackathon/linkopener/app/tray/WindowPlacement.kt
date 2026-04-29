package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.Point
import java.awt.Rectangle

/**
 * Picks which screen's bounds to use for centering a freshly-opened window.
 *
 * `screenBounds` is the list of every connected display's `Rectangle` (in
 * the unified Java screen coordinate space — primary monitor at (0, 0),
 * secondaries spread out by their `bounds.x` / `bounds.y`). `fallback` is
 * the primary monitor's bounds, used when the cursor isn't on any visible
 * display — that happens during display reconfigure or with locked
 * sessions.
 *
 * Pure function so we can unit-test it without instantiating real
 * `GraphicsDevice`s.
 */
internal fun pickScreenBoundsForCursor(
    screenBounds: List<Rectangle>,
    fallback: Rectangle,
    cursor: Point,
): Rectangle = screenBounds.firstOrNull { it.contains(cursor) } ?: fallback

/**
 * Computes the top-left [WindowPosition] that places a [windowWidth] ×
 * [windowHeight] window in the geometric center of [screenBounds].
 *
 * Treats `Rectangle` units 1-for-1 as `Dp` — same convention the rest of
 * `TrayHost` uses for cursor-derived positioning. On macOS the JDK reports
 * cursor and bounds in logical (point) coords already, and Compose's `Dp`
 * map to those points 1:1, so the math is straightforward. On Windows
 * with mixed-DPI multi-monitor the per-monitor scale factor can introduce
 * a few pixels of error in the center, which is below the visual
 * discrimination threshold for "is this window roughly in the middle".
 */
internal fun centerInBounds(
    screenBounds: Rectangle,
    windowWidth: Dp,
    windowHeight: Dp,
): WindowPosition {
    val x = screenBounds.x + (screenBounds.width - windowWidth.value.toInt()) / 2
    val y = screenBounds.y + (screenBounds.height - windowHeight.value.toInt()) / 2
    return WindowPosition(x.dp, y.dp)
}
