package dev.hackathon.linkopener.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import dev.hackathon.linkopener.platform.HostOs
import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Minimal replacement for Compose's `Tray` composable that gives us:
 *
 * 1. **Auto-tinted icons on macOS.** [prepareTrayImage] marks the rasterized
 *    BufferedImage as a template image so the system fills it with the menu
 *    bar's appropriate color (no hardcoded white/black, no JNA).
 * 2. **Inverted click behavior.** Compose's Tray opens the popup on left-click
 *    on macOS and routes right-click to `onAction`. We want the opposite —
 *    left-click opens Settings, right-click shows the menu — so we attach a
 *    [TrayMouseHandler] directly and **do not** assign `tray.popupMenu`.
 *
 * The composable owns the `SystemTray` registration via [DisposableEffect] —
 * it adds the icon when the composition enters and removes it on dispose, so
 * the icon goes away cleanly when the parent composable is unmounted (e.g.
 * the Quit menu item flips the application off).
 */
@Composable
internal fun ApplicationScope.NativeTray(
    iconPainter: Painter,
    tooltip: String,
    hostOs: HostOs,
    onLeftClick: () -> Unit,
    menuItems: List<TrayMenuItem>,
) {
    if (!SystemTray.isSupported()) {
        DisposableEffect(Unit) {
            // Match Compose's own behavior: warn but don't crash. Apps without
            // tray support (some Linux DEs) still need to start.
            System.err.println("SystemTray is not supported on this platform; skipping tray icon.")
            onDispose {}
        }
        return
    }

    // Linux tray-icon visibility is famously brittle (XEmbed timing, applet
    // version mismatch, GTK2/3 fallbacks, …). Print what AWT actually sees
    // so a "no icon" report comes with enough diagnostic context to tell
    // whether we hit the JDK-side support gate or a pure render bug.
    if (hostOs == HostOs.Linux) {
        DisposableEffect(Unit) {
            val tray = SystemTray.getSystemTray()
            System.err.println(
                "[tray] Linux SystemTray.isSupported=true, " +
                    "trayIconSize=${tray.trayIconSize.width}x${tray.trayIconSize.height}",
            )
            onDispose {}
        }
    }

    val image = remember(iconPainter, hostOs) { prepareTrayImage(iconPainter, hostOs) }

    val currentOnLeftClick by rememberUpdatedState(onLeftClick)
    val currentMenuItems by rememberUpdatedState(menuItems)

    val invoker = remember { createTrayMenuInvoker() }

    val trayIcon = remember {
        TrayIcon(image).apply {
            isImageAutoSize = true
        }
    }

    SideEffect {
        if (trayIcon.image !== image) trayIcon.image = image
        if (trayIcon.toolTip != tooltip) trayIcon.toolTip = tooltip
    }

    DisposableEffect(Unit) {
        val handler = TrayMouseHandler(
            onLeftClick = { currentOnLeftClick() },
            onPopupTrigger = { x, y ->
                // Rebuild the popup each time so the labels/handlers reflect
                // the latest captured state (e.g. localized strings after a
                // language switch). Cheap — three menu items.
                val popup = buildTrayMenu(currentMenuItems)
                showTrayMenuAt(popup, invoker, x, y)
            },
        )
        trayIcon.addMouseListener(handler)
        SystemTray.getSystemTray().add(trayIcon)
        onDispose {
            trayIcon.removeMouseListener(handler)
            SystemTray.getSystemTray().remove(trayIcon)
            invoker.dispose()
        }
    }
}
