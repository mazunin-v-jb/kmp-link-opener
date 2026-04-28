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
import dev.hackathon.linkopener.ui.picker.PickerState
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

    val systemLanguageTag = remember { Locale.getDefault().language }

    val strings = remember(settings.language, systemLanguageTag) {
        resolveStrings(settings.language, systemLanguageTag)
    }

    val trayIconPainter = remember { loadTrayIconPainter() }
    val appIconPainter = remember { loadAppIconPainter() }

    val pickerState by container.pickerCoordinator.state.collectAsState()

    var settingsAnchor by remember { mutableStateOf<WindowPosition?>(null) }

    Tray(
        icon = trayIconPainter,
        tooltip = strings.appName,
        menu = {
            Item(strings.trayMenuSettings, onClick = { settingsAnchor = currentCursorPosition() })
            // TODO: remove the dev-only "Test picker" entry before public
            //  release. It's here so the picker can be exercised without
            //  packaging + installing the app as the OS default browser.
            Item(
                strings.trayMenuTestPicker,
                onClick = { container.pickerCoordinator.handleIncomingUrl("https://example.com/?utm=picker-test") },
            )
            Item(strings.trayMenuQuit, onClick = onExit)
        },
    )

    val currentPickerState = pickerState
    if (currentPickerState is PickerState.Showing) {
        LinkOpenerTheme(theme = settings.theme) {
            PickerWindow(
                url = currentPickerState.url,
                browsers = currentPickerState.browsers,
                strings = strings,
                onPick = container.pickerCoordinator::pickBrowser,
                onDismiss = container.pickerCoordinator::dismiss,
            )
        }
    }

    val anchor = settingsAnchor
    if (anchor != null) {
        val windowState = rememberWindowState(
            position = anchor,
            width = 960.dp,
            height = 640.dp,
        )
        Window(
            onCloseRequest = { settingsAnchor = null },
            title = strings.appName + strings.trayWindowSettingsSuffix,
            state = windowState,
            icon = appIconPainter,
            alwaysOnTop = true,
        ) {
            LinkOpenerTheme(theme = settings.theme) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    strings = strings,
                    appVersion = appInfo.version,
                    currentOs = container.currentOs,
                    appIconPainter = appIconPainter,
                    onCloseRequest = { settingsAnchor = null },
                )
            }
        }
    }
}

private fun currentCursorPosition(): WindowPosition {
    val location = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    return WindowPosition(x = location.x.dp, y = location.y.dp)
}
