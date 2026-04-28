package dev.hackathon.linkopener.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.hackathon.linkopener.app.AppContainer
import dev.hackathon.linkopener.ui.settings.SettingsScreen
import dev.hackathon.linkopener.ui.strings.resolveStrings
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme
import java.awt.MouseInfo
import java.util.Locale

@Composable
fun ApplicationScope.TrayHost(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val appInfo = remember(container) { container.getAppInfoUseCase() }
    val settingsViewModel = remember(container) { container.newSettingsViewModel() }
    val settings by settingsViewModel.settings.collectAsState()

    // Captured once at startup. Locale.getDefault() reads the JVM's locale
    // which on macOS reflects the system "Region & Language" preference at
    // the time the app launched. Changing OS locale will require restart —
    // acceptable for the prototype.
    val systemLanguageTag = remember { Locale.getDefault().language }

    val strings = remember(settings.language, systemLanguageTag) {
        resolveStrings(settings.language, systemLanguageTag)
    }

    var settingsAnchor by remember { mutableStateOf<WindowPosition?>(null) }

    Tray(
        icon = remember { PlaceholderTrayIcon() },
        tooltip = appInfo.name,
        menu = {
            Item(strings.trayMenuSettings, onClick = { settingsAnchor = currentCursorPosition() })
            Item(strings.trayMenuQuit, onClick = onExit)
        },
    )

    val anchor = settingsAnchor
    if (anchor != null) {
        val windowState = rememberWindowState(
            position = anchor,
            width = 720.dp,
            height = 480.dp,
        )
        Window(
            onCloseRequest = { settingsAnchor = null },
            title = appInfo.name + strings.trayWindowSettingsSuffix,
            state = windowState,
            alwaysOnTop = true,
        ) {
            LinkOpenerTheme(theme = settings.theme) {
                SettingsScreen(viewModel = settingsViewModel, strings = strings)
            }
        }
    }
}

private fun currentCursorPosition(): WindowPosition {
    val location = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    return WindowPosition(x = location.x.dp, y = location.y.dp)
}
