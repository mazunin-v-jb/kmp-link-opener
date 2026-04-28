package dev.hackathon.linkopener.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.ui.picker.BrowserPickerScreen
import dev.hackathon.linkopener.ui.strings.Strings
import java.awt.MouseInfo
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ApplicationScope.PickerWindow(
    url: String,
    browsers: List<Browser>,
    strings: Strings,
    onPick: (Browser) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowState = rememberWindowState(
        position = currentCursorWindowPosition(),
        width = 320.dp,
        height = 320.dp,
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
            strings = strings,
            onPick = onPick,
        )
    }
}

private fun currentCursorWindowPosition(): WindowPosition {
    val location = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    return WindowPosition(x = location.x.dp, y = location.y.dp)
}
