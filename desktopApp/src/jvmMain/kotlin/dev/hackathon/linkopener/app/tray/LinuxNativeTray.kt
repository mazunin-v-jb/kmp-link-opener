package dev.hackathon.linkopener.app.tray

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import com.kdroid.composetray.tray.api.Tray as KdTray
import com.kdroid.composetray.utils.isMenuBarInDarkMode

/**
 * Linux-only tray icon. Wraps the
 * [ComposeNativeTray](https://github.com/kdroidFilter/ComposeNativeTray)
 * library, which talks `libayatana-appindicator` over D-Bus — the
 * StatusNotifierItem-based protocol Cinnamon / GNOME / KDE actually
 * render in their panel applets.
 *
 * Why this exists separately from the AWT-based [NativeTray]: AWT's
 * `SystemTray` uses XEmbed, which has been silently ignored by Cinnamon
 * 5.x and modern GNOME Shell for years. Cryptomator hit the same wall
 * and ended up calling appindicator via JDK 20 FFI; we rely on
 * ComposeNativeTray's JNA bindings instead so we stay on JDK 17 and
 * keep the Compose-native API shape.
 *
 * On Linux the tray protocol funnels every interaction through a single
 * menu (no left-vs-right click distinction), so we bake the same
 * "Settings + Quit" pair we'd surface from the AWT popup directly into
 * the menu items. The library calls `primaryAction` on a single click
 * (Cinnamon / KDE) or a double click (GNOME); we wire it to the same
 * `onLeftClick` callback the AWT path uses, so a quick click still
 * lands in Settings on DEs that route a click separately.
 */
@Composable
internal fun ApplicationScope.LinuxNativeTray(
    iconPainter: Painter,
    tooltip: String,
    onLeftClick: () -> Unit,
    menuItems: List<TrayMenuItem>,
) {
    // Cinnamon / GNOME / KDE panel applets ignore the colour data in
    // pixmaps the StatusNotifierItem protocol delivers and treat the
    // icon as a flat silhouette. Pass the colour brand logo straight
    // through and it ends up as a black blob on a dark panel — invisible.
    // Tint to contrast against the panel theme instead. Trades the
    // brand colour for legibility, which is the usual Linux tray
    // convention (Slack / Discord / Signal / VS Code all do this).
    val tint = if (isMenuBarInDarkMode()) Color.White else Color.Black
    KdTray(
        iconContent = {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        },
        tooltip = tooltip,
        primaryAction = onLeftClick,
    ) {
        menuItems.forEach { item ->
            // The menu DSL handles divider rendering and shortcut
            // wiring for us. Our items are simple click handlers, so a
            // bare `Item(label) { … }` is enough.
            Item(label = item.label, onClick = item.onClick)
        }
    }
}
