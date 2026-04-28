package dev.hackathon.linkopener.app.tray

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.ui.picker.BrowserPickerScreen
import java.awt.MouseInfo
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.PickerWindow(
    url: String,
    browsers: List<Browser>,
    onPick: (Browser) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowState = rememberWindowState(
        position = currentCursorWindowPosition(),
        width = PICKER_WIDTH,
        height = PICKER_HEIGHT_COLLAPSED,
    )
    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        title = "Link Opener — Picker",
        decoration = WindowDecoration.Undecorated(),
        transparent = true,
        resizable = false,
        focusable = true,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        // Lift the NSWindow above fullscreen apps' overlay. AWT alone keeps us
        // at NSFloatingWindowLevel which sits below them.
        LaunchedEffect(Unit) {
            MacOsAlwaysOnTopOverFullScreen.apply(window)
        }

        // Auto-dismiss on focus loss. Skip the very first emission because
        // the window may briefly start unfocused before the OS hands it focus.
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(Unit) {
            snapshotFlow { windowInfo.isWindowFocused }
                .drop(1)
                .collect { focused ->
                    if (!focused) onDismiss()
                }
        }
        BrowserPickerScreen(
            url = url,
            browsers = browsers,
            onPick = onPick,
            // "Show all" grows the popup downward so the previously hidden
            // browsers fit alongside the original three, with the whole list
            // scrollable inside.
            onExpand = {
                windowState.size = DpSize(PICKER_WIDTH, PICKER_HEIGHT_EXPANDED)
            },
            // Header doubles as a drag handle so users can reposition the
            // popup without a title bar (we run undecorated). WindowDraggableArea
            // is a WindowScope extension, so resolution depends on this lambda
            // being defined inside the Window {} block — which it is.
            headerWrapper = { content -> WindowDraggableArea { content() } },
        )
    }
}

// Collapsed fits 3 rows + "Show all" + the URL header. Expanded adds three
// more rows of viewport — see EXPANDED_VISIBLE_COUNT in BrowserPickerScreen.
private val PICKER_WIDTH = 320.dp
private val PICKER_HEIGHT_COLLAPSED = 320.dp
private val PICKER_HEIGHT_EXPANDED = 480.dp

private fun currentCursorWindowPosition(): WindowPosition {
    val location = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    return WindowPosition(x = location.x.dp, y = location.y.dp)
}
