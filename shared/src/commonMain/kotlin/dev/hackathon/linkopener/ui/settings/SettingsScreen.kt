package dev.hackathon.linkopener.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
        Box(modifier = Modifier.fillMaxSize()) {
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
                            // Plain `when` instead of Crossfade — Crossfade caches the
                            // content lambda per targetState, so when only the locale
                            // changed (activeSection unchanged) the lambda wasn't
                            // re-invoked and the visible section kept its old strings
                            // until the user navigated. With direct rendering each
                            // SettingsScreen recomposition (including the one
                            // triggered by settings.language flow) re-runs the active
                            // branch and stringResource() picks up the new locale.
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
                                    onToggle = viewModel::onBrowserExclusionToggled,
                                    onMoveUp = viewModel::onMoveBrowserUp,
                                    onMoveDown = viewModel::onMoveBrowserDown,
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
