package dev.hackathon.linkopener.app.tray

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import dev.hackathon.linkopener.app.AppContainer
import dev.hackathon.linkopener.ui.settings.SettingsScreen

@Composable
fun ApplicationScope.TrayHost(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val appInfo = remember(container) { container.getAppInfoUseCase() }
    var settingsOpen by remember { mutableStateOf(false) }

    Tray(
        icon = remember { PlaceholderTrayIcon() },
        tooltip = appInfo.name,
        menu = {
            Item("Settings", onClick = { settingsOpen = true })
            Item("Quit", onClick = onExit)
        },
    )

    if (settingsOpen) {
        Window(
            onCloseRequest = { settingsOpen = false },
            title = "${appInfo.name} — Settings",
            state = rememberWindowState(width = 720.dp, height = 480.dp),
        ) {
            MaterialTheme {
                SettingsScreen()
            }
        }
    }
}
