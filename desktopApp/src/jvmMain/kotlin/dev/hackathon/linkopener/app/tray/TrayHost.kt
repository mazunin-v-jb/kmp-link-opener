package dev.hackathon.linkopener.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.hackathon.linkopener.app.DebugFlags
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.picker.PickerState
import dev.hackathon.linkopener.ui.settings.SettingsScreen
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme
import java.awt.FileDialog
import java.awt.Frame
import java.awt.MouseInfo
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    settings: AppSettings,
    settingsViewModel: SettingsViewModel,
    appVersion: String,
    onExit: () -> Unit,
) {
    // Tray + window-decoration icon both use the SVG-backed full-color logo.
    // Skia rasterizes the vector at the OS-requested size, so no manual
    // pre-scale is needed (the previous PNG path went through AWT's
    // nearest-neighbor downscale and crunched fine line art).
    val appIconPainter = AppIcons.AppLogoV2

    val pickerState by container.pickerCoordinator.state.collectAsState()

    var settingsAnchor by remember { mutableStateOf<WindowPosition?>(null) }

    // Nudges flow into the open Settings window when a second copy of the app
    // pings us while Settings is already visible. Owned here (not in
    // AppContainer) because the dispatch decision — open vs nudge — depends
    // on `settingsAnchor`, which is composition-local state.
    val settingsNudges = remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }
    LaunchedEffect(container) {
        container.activationRequests.collect {
            if (settingsAnchor == null) {
                settingsAnchor = currentCursorPosition()
            } else {
                settingsNudges.tryEmit(Unit)
            }
        }
    }

    val appName = stringResource(Res.string.app_name)

    Tray(
        icon = appIconPainter,
        tooltip = appName,
        menu = {
            Item(
                stringResource(Res.string.tray_menu_settings),
                onClick = { settingsAnchor = currentCursorPosition() },
            )
            // Dev-only "Test picker" — gated on the same DebugFlags.enabled
            // toggle as the startup discovery dump. Stamped public builds get
            // launched without the flag and never see this entry.
            if (DebugFlags.enabled) {
                Item(
                    stringResource(Res.string.tray_menu_test_picker),
                    onClick = { container.pickerCoordinator.handleIncomingUrl("https://example.com/?utm=picker-test") },
                )
            }
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
                    onCloseRequest = { settingsAnchor = null },
                    onAddBrowserClick = {
                        // Native macOS file picker via AWT — bundles like .app
                        // are selectable as files. The path is forwarded to
                        // the VM, which delegates to AddManualBrowserUseCase.
                        val path = pickBrowserAppPath()
                        settingsViewModel.onManualBrowserPicked(path)
                    },
                    nudges = settingsNudges,
                )
            }
        }
    }
}

private fun currentCursorPosition(): WindowPosition {
    val location = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    return WindowPosition(x = location.x.dp, y = location.y.dp)
}

private fun pickBrowserAppPath(): String? {
    // `apple.awt.use-file-dialog-packages=true` tells AWT FileDialog to treat
    // macOS bundles (.app / .bundle / .framework / …) as selectable files
    // instead of folders. Without it, double-clicking a .app navigates *into*
    // it (Contents/, MacOS/, …) and the user can't actually pick the bundle.
    // Property must be set before the dialog opens; AWT reads it at show time.
    System.setProperty("apple.awt.use-file-dialog-packages", "true")
    val dialog = FileDialog(null as Frame?, "Choose a browser app", FileDialog.LOAD).apply {
        directory = "/Applications"
        // Visual filter — only `.app` rows show up. The metadata extractor
        // re-validates the path defensively, so this is convenience, not a
        // security boundary.
        setFilenameFilter { _, name -> name.endsWith(".app") }
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, name).absolutePath
}
