package dev.hackathon.linkopener.app.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.hackathon.linkopener.app.AppContainer
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.picker.PickerState
import dev.hackathon.linkopener.ui.settings.SettingsScreen
import dev.hackathon.linkopener.ui.settings.SettingsViewModel
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_name
import kmp_link_opener.shared.generated.resources.tray_menu_quit
import kmp_link_opener.shared.generated.resources.tray_menu_settings
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
    val browserIcons by container.browserIconRepository.icons.collectAsState()

    // `null` = window closed. A non-null anchor flips the visibility on and
    // also fixes the spawn position for that opening — closing + reopening
    // recomputes it, so the window keeps re-centering on whichever screen
    // the cursor is on now.
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
                settingsAnchor = currentScreenCenterPosition(SETTINGS_WIDTH, SETTINGS_HEIGHT)
            } else {
                settingsNudges.tryEmit(Unit)
            }
        }
    }

    val appName = stringResource(Res.string.app_name)
    val settingsLabel = stringResource(Res.string.tray_menu_settings)
    val quitLabel = stringResource(Res.string.tray_menu_quit)

    NativeTray(
        iconPainter = appIconPainter,
        tooltip = appName,
        hostOs = container.currentOs,
        onLeftClick = {
            settingsAnchor = currentScreenCenterPosition(SETTINGS_WIDTH, SETTINGS_HEIGHT)
        },
        menuItems = listOf(
            TrayMenuItem(settingsLabel) {
                settingsAnchor = currentScreenCenterPosition(SETTINGS_WIDTH, SETTINGS_HEIGHT)
            },
            TrayMenuItem(quitLabel, onExit),
        ),
    )

    val currentPickerState = pickerState
    if (currentPickerState is PickerState.Showing) {
        LinkOpenerTheme(theme = settings.theme) {
            PickerWindow(
                url = currentPickerState.url,
                browsers = currentPickerState.browsers,
                icons = browserIcons,
                runningBrowserIds = currentPickerState.runningBrowserIds,
                // Drives LocalAppLocale inside the picker subcomposition so
                // string-using composables there recompose on language
                // switch the same way SettingsScreen does.
                localeNonce = settings.language.name,
                onPick = container.pickerCoordinator::pickBrowser,
                onDismiss = container.pickerCoordinator::dismiss,
            )
        }
    }

    val anchor = settingsAnchor
    if (anchor != null) {
        val windowState = rememberWindowState(
            position = anchor,
            width = SETTINGS_WIDTH,
            height = SETTINGS_HEIGHT,
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

// Single source of truth for the Settings window dimensions — both the
// initial position calculation and `rememberWindowState` size derive from
// here, so a tweak to the window size doesn't drift the centering math.
private val SETTINGS_WIDTH = 960.dp
private val SETTINGS_HEIGHT = 640.dp

/**
 * Centers a window of [windowWidth] × [windowHeight] on whichever monitor
 * the mouse cursor currently lives on. Works around the previous behavior
 * of opening at the cursor (i.e. at the tray icon), which on Windows put
 * the window at the bottom of the screen because the taskbar is anchored
 * there.
 *
 * Multi-monitor handled by [pickScreenBoundsForCursor]: walk every
 * connected display and pick the one whose bounds contain the cursor,
 * falling back to the primary screen if the cursor isn't on any visible
 * display (locked session, between monitors during reconfigure, …).
 *
 * The actual centering math is in [centerInBounds] for unit-testing —
 * this function is just the AWT side-effect glue around it.
 */
private fun currentScreenCenterPosition(
    windowWidth: Dp,
    windowHeight: Dp,
): WindowPosition {
    val cursor = MouseInfo.getPointerInfo()?.location ?: return WindowPosition.PlatformDefault
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val allBounds = env.screenDevices.map { it.defaultConfiguration.bounds }
    val fallback = env.defaultScreenDevice.defaultConfiguration.bounds
    val targetBounds = pickScreenBoundsForCursor(allBounds, fallback, cursor)
    return centerInBounds(targetBounds, windowWidth, windowHeight)
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
