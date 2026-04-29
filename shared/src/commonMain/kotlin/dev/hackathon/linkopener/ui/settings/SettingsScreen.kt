package dev.hackathon.linkopener.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.ui.settings.components.BottomNavBar
import dev.hackathon.linkopener.ui.settings.components.NotDefaultBanner
import dev.hackathon.linkopener.ui.settings.components.SettingsTopAppBar
import dev.hackathon.linkopener.ui.settings.components.Sidebar
import dev.hackathon.linkopener.ui.settings.sections.AppearanceSection
import dev.hackathon.linkopener.ui.settings.sections.DefaultBrowserSection
import dev.hackathon.linkopener.ui.settings.sections.ExclusionsSection
import dev.hackathon.linkopener.ui.settings.sections.LanguageSection
import dev.hackathon.linkopener.ui.settings.sections.RulesSection
import dev.hackathon.linkopener.ui.settings.sections.SystemSection
import dev.hackathon.linkopener.ui.strings.LocalAppLocale
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.nudge_already_running
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    currentOs: HostOs,
    onCloseRequest: () -> Unit = {},
    // No-op default keeps existing call sites and tests compiling. The
    // desktopApp wires this to a native AWT FileDialog and forwards the
    // chosen path into `viewModel.onManualBrowserPicked(...)`.
    onAddBrowserClick: () -> Unit = {},
    // Fires when a second copy of the app pinged us while Settings is open.
    // Default: empty flow — keeps existing call sites and tests compiling.
    nudges: SharedFlow<Unit> = remember { MutableSharedFlow() },
) {
    val settings by viewModel.settings.collectAsState()
    val browsers by viewModel.browsers.collectAsState()
    val isDefault by viewModel.isDefaultBrowser.collectAsState()
    val manualAddNotice by viewModel.manualAddNotice.collectAsState()
    // Empty map when icons aren't wired (e.g. unit tests with the no-arg VM
    // factory) — every row falls back to the letter avatar.
    val browserIcons by (viewModel.browserIcons
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }
        ).collectAsState()
    var activeSection by remember { mutableStateOf(NavSection.DefaultBrowser) }
    val snackbarHostState = remember { SnackbarHostState() }
    val nudgeMessage = stringResource(Res.string.nudge_already_running)
    LaunchedEffect(nudges, nudgeMessage) {
        nudges.collect {
            snackbarHostState.showSnackbar(message = nudgeMessage, withDismissAction = true)
        }
    }

    // Provide a locale nonce so children that don't take any settings-derived
    // parameter still recompose when the user switches language — without it
    // Compose's smart-skipping leaves TopAppBar / Sidebar / banner stuck on
    // the previous locale until something else invalidates them.
    CompositionLocalProvider(LocalAppLocale provides settings.language.name) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 600dp is Material's typical "compact / medium" breakpoint —
            // below this we're on a phone in portrait and the 240dp sidebar
            // would eat most of the available width. Above, classic
            // desktop layout.
            val compact = maxWidth < 600.dp
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsTopAppBar(
                        onRefresh = viewModel::refresh,
                        onCloseRequest = onCloseRequest,
                    )
                    if (!isDefault) {
                        NotDefaultBanner(
                            canOpenSettings = viewModel.canOpenSystemSettings,
                            onOpenSettings = viewModel::openSystemSettings,
                            onSelectDefaultSection = { activeSection = NavSection.DefaultBrowser },
                        )
                    }
                    if (compact) {
                        // Compact: content fills the row above a bottom nav.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            ActiveSection(
                                activeSection = activeSection,
                                viewModel = viewModel,
                                settings = settings,
                                browsers = browsers,
                                browserIcons = browserIcons,
                                manualAddNotice = manualAddNotice,
                                isDefault = isDefault,
                                currentOs = currentOs,
                                onAddBrowserClick = onAddBrowserClick,
                            )
                        }
                        BottomNavBar(
                            activeSection = activeSection,
                            onSelect = { activeSection = it },
                        )
                    } else {
                        // Desktop / tablet: 240dp sidebar + content.
                        Row(modifier = Modifier.fillMaxSize()) {
                            Sidebar(
                                appVersion = appVersion,
                                activeSection = activeSection,
                                onSelect = { activeSection = it },
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                ActiveSection(
                                    activeSection = activeSection,
                                    viewModel = viewModel,
                                    settings = settings,
                                    browsers = browsers,
                                    browserIcons = browserIcons,
                                    manualAddNotice = manualAddNotice,
                                    isDefault = isDefault,
                                    currentOs = currentOs,
                                    onAddBrowserClick = onAddBrowserClick,
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

/**
 * The body of the active settings section. Extracted so the compact
 * (BottomNavBar) and wide (Sidebar) layouts share the same per-section
 * dispatch table — only the surrounding chrome differs.
 *
 * Plain `when` instead of Crossfade — Crossfade caches the content lambda
 * per targetState, so when only the locale changed (activeSection
 * unchanged) the lambda wasn't re-invoked and the visible section kept
 * its old strings until the user navigated. Direct rendering means each
 * SettingsScreen recomposition re-runs the active branch and
 * stringResource() picks up the new locale.
 */
@Composable
private fun ActiveSection(
    activeSection: NavSection,
    viewModel: SettingsViewModel,
    settings: dev.hackathon.linkopener.core.model.AppSettings,
    browsers: BrowsersState,
    browserIcons: Map<String, androidx.compose.ui.graphics.ImageBitmap>,
    manualAddNotice: ManualAddNotice?,
    isDefault: Boolean,
    currentOs: HostOs,
    onAddBrowserClick: () -> Unit,
) {
    when (activeSection) {
        NavSection.DefaultBrowser -> DefaultBrowserSection(
            currentOs = currentOs,
            isDefault = isDefault,
            canOpenSettings = viewModel.canOpenSystemSettings,
            onOpenSettings = viewModel::openSystemSettings,
        )
        NavSection.Appearance ->
            AppearanceSection(settings.theme, viewModel::onThemeSelected)
        NavSection.Language ->
            LanguageSection(settings.language, viewModel::onLanguageSelected)
        NavSection.System -> SystemSection(
            autoStart = settings.autoStartEnabled,
            onAutoStartChange = viewModel::onAutoStartChanged,
            showBrowserProfiles = settings.showBrowserProfiles,
            onShowBrowserProfilesChange = viewModel::onShowBrowserProfilesChanged,
        )
        NavSection.Exclusions -> ExclusionsSection(
            browsersState = browsers,
            excluded = settings.excludedBrowserIds,
            manualBrowserIds = settings.manualBrowsers
                .mapTo(HashSet()) { BrowserId(it.applicationPath) },
            manualAddNotice = manualAddNotice,
            icons = browserIcons,
            onToggle = viewModel::onBrowserExclusionToggled,
            onReorder = viewModel::onReorderBrowsers,
            onRemoveManual = viewModel::onRemoveManualBrowser,
            onAddBrowserClick = onAddBrowserClick,
            onDismissManualAddNotice = viewModel::dismissManualAddNotice,
            onRetry = viewModel::refreshBrowsers,
        )
        NavSection.Rules -> RulesSection(
            rules = settings.rules,
            availableBrowsers = (browsers as? BrowsersState.Loaded)
                ?.browsers
                ?: emptyList(),
            onAddRule = viewModel::onAddRule,
            onRemoveRule = viewModel::onRemoveRule,
            onMoveRule = viewModel::onMoveRule,
            onUpdateRulePattern = viewModel::onUpdateRulePattern,
            onUpdateRuleBrowser = viewModel::onUpdateRuleBrowser,
        )
    }
}
