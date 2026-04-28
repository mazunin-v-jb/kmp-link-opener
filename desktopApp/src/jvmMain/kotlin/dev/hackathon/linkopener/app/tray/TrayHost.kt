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
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme
import java.awt.MouseInfo
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_name
import kmp_link_opener.shared.generated.resources.tray_menu_quit
import kmp_link_opener.shared.generated.resources.tray_menu_settings
import kmp_link_opener.shared.generated.resources.tray_menu_test_picker
import kmp_link_opener.shared.generated.resources.tray_window_settings_suffix
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApplicationScope.TrayHost(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val appInfo = remember(container) { container.getAppInfoUseCase() }
    val settingsViewModel = remember(container) { container.newSettingsViewModel() }
    val settings by settingsViewModel.settings.collectAsState()

    // JVM Locale.setDefault is now applied upstream — at AppContainer init
    // for the loaded language and inside SettingsViewModel.onLanguageSelected
    // (synchronously, on the click thread) for user changes. By the time
    // any composition runs, Compose Resources sees the right locale on its
    // own pass — no in-composition side effects, no race between the main
    // composition and the Window subcomposition.

    TrayHostBody(
        container = container,
        settings = settings,
        settingsViewModel = settingsViewModel,
        appVersion = appInfo.version,
        onExit = onExit,
    )
}

@Composable
private fun ApplicationScope.TrayHostBody(
    container: AppContainer,
    settings: dev.hackathon.linkopener.core.model.AppSettings,
    settingsViewModel: dev.hackathon.linkopener.ui.settings.SettingsViewModel,
    appVersion: String,
    onExit: () -> Unit,
) {
    val trayIconPainter = remember { loadTrayIconPainter() }
    val appIconPainter = remember { loadAppIconPainter() }

    val pickerState by container.pickerCoordinator.state.collectAsState()

    var settingsAnchor by remember { mutableStateOf<WindowPosition?>(null) }

    val appName = stringResource(Res.string.app_name)

    Tray(
        icon = trayIconPainter,
        tooltip = appName,
        menu = {
            Item(
                stringResource(Res.string.tray_menu_settings),
                onClick = { settingsAnchor = currentCursorPosition() },
            )
            // TODO: remove the dev-only "Test picker" entry before public
            //  release. It's here so the picker can be exercised without
            //  packaging + installing the app as the OS default browser.
            Item(
                stringResource(Res.string.tray_menu_test_picker),
                onClick = { container.pickerCoordinator.handleIncomingUrl("https://example.com/?utm=picker-test") },
            )
            Item(stringResource(Res.string.tray_menu_quit), onClick = onExit)
        },
    )

    val currentPickerState = pickerState
    if (currentPickerState is PickerState.Showing) {
        LinkOpenerTheme(theme = settings.theme) {
            PickerWindow(
                url = currentPickerState.url,
                browsers = currentPickerState.browsers,
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
            title = appName + stringResource(Res.string.tray_window_settings_suffix),
            state = windowState,
            icon = appIconPainter,
            alwaysOnTop = true,
        ) {
            LinkOpenerTheme(theme = settings.theme) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    appVersion = appVersion,
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
