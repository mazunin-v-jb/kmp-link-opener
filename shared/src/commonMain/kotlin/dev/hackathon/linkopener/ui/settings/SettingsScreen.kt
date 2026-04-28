package dev.hackathon.linkopener.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.core.model.uiLabel
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.strings.LocalAppLocale
import dev.hackathon.linkopener.ui.strings.defaultBrowserInstructions
import dev.hackathon.linkopener.ui.strings.languageLabel
import dev.hackathon.linkopener.ui.strings.themeLabel
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LocalIsDarkMode
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.add_browser
import kmp_link_opener.shared.generated.resources.add_rule
import kmp_link_opener.shared.generated.resources.rule_no_browsers
import kmp_link_opener.shared.generated.resources.rule_no_rules
import kmp_link_opener.shared.generated.resources.rule_pattern_placeholder
import kmp_link_opener.shared.generated.resources.rule_remove_content_description
import kmp_link_opener.shared.generated.resources.section_rules
import kmp_link_opener.shared.generated.resources.app_language
import kmp_link_opener.shared.generated.resources.app_name
import kmp_link_opener.shared.generated.resources.banner_not_default_body
import kmp_link_opener.shared.generated.resources.banner_not_default_title
import kmp_link_opener.shared.generated.resources.banner_open_settings
import kmp_link_opener.shared.generated.resources.browsers_empty
import kmp_link_opener.shared.generated.resources.browsers_error_prefix
import kmp_link_opener.shared.generated.resources.browsers_loading
import kmp_link_opener.shared.generated.resources.close as closeStr
import kmp_link_opener.shared.generated.resources.default_browser_instructions_header
import kmp_link_opener.shared.generated.resources.default_browser_open_system_settings
import kmp_link_opener.shared.generated.resources.default_browser_packaging_note
import kmp_link_opener.shared.generated.resources.default_browser_status_no
import kmp_link_opener.shared.generated.resources.default_browser_status_yes
import kmp_link_opener.shared.generated.resources.excluded as excludedStr
import kmp_link_opener.shared.generated.resources.help as helpStr
import kmp_link_opener.shared.generated.resources.included as includedStr
import kmp_link_opener.shared.generated.resources.manual_add_dismiss
import kmp_link_opener.shared.generated.resources.manual_add_duplicate
import kmp_link_opener.shared.generated.resources.manual_add_invalid_prefix
import kmp_link_opener.shared.generated.resources.manual_add_self
import kmp_link_opener.shared.generated.resources.move_down
import kmp_link_opener.shared.generated.resources.move_up
import kmp_link_opener.shared.generated.resources.nudge_already_running
import kmp_link_opener.shared.generated.resources.remove_manual_browser
import kmp_link_opener.shared.generated.resources.retry as retryStr
import kmp_link_opener.shared.generated.resources.refresh_action
import kmp_link_opener.shared.generated.resources.search_browsers
import kmp_link_opener.shared.generated.resources.section_appearance
import kmp_link_opener.shared.generated.resources.section_browser_exclusions
import kmp_link_opener.shared.generated.resources.section_default_browser
import kmp_link_opener.shared.generated.resources.section_language
import kmp_link_opener.shared.generated.resources.section_system
import kmp_link_opener.shared.generated.resources.settings_title
import kmp_link_opener.shared.generated.resources.start_at_login
import kmp_link_opener.shared.generated.resources.start_at_login_description
import kmp_link_opener.shared.generated.resources.theme_mode
import kmp_link_opener.shared.generated.resources.version_prefix
import org.jetbrains.compose.resources.stringResource

private enum class NavSection { DefaultBrowser, Appearance, Language, System, Exclusions, Rules }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    currentOs: HostOs,
    appIconPainter: Painter? = null,
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
            TopAppBar(
                appIconPainter = appIconPainter,
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
                        NavSection.Appearance -> AppearanceSection(settings.theme, viewModel::onThemeSelected)
                        NavSection.Language -> LanguageSection(settings.language, viewModel::onLanguageSelected)
                        NavSection.System -> SystemSection(settings.autoStartEnabled, viewModel::onAutoStartChanged)
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
                            availableBrowsers = (browsers as? BrowsersState.Loaded)?.browsers ?: emptyList(),
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

// region top bar + banner

@Composable
private fun TopAppBar(
    appIconPainter: Painter?,
    onRefresh: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    LocalAppLocale.current  // see LocalAppLocale doc — keeps strings live across language changes
    val appName = stringResource(Res.string.app_name)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceContainerLow(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (appIconPainter != null) {
                Box(modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = appIconPainter,
                        contentDescription = appName,
                        tint = Color.Unspecified,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = AppIcons.Refresh,
                    contentDescription = stringResource(Res.string.refresh_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { /* TODO: help action */ }) {
                Icon(
                    imageVector = AppIcons.Help,
                    contentDescription = stringResource(Res.string.helpStr),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCloseRequest) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(Res.string.closeStr),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotDefaultBanner(
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
    onSelectDefaultSection: () -> Unit,
) {
    LocalAppLocale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(onClick = onSelectDefaultSection),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.banner_not_default_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(Res.string.banner_not_default_body),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                )
            }
            if (canOpenSettings) {
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(Res.string.banner_open_settings))
                }
            }
        }
    }
}

// endregion

// region sidebar

@Composable
private fun Sidebar(
    appVersion: String,
    activeSection: NavSection,
    onSelect: (NavSection) -> Unit,
) {
    LocalAppLocale.current
    Surface(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight(),
        color = surfaceContainerLow(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.version_prefix) + appVersion,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NavItem(AppIcons.BrowserUpdated, stringResource(Res.string.section_default_browser),
                active = activeSection == NavSection.DefaultBrowser,
                onClick = { onSelect(NavSection.DefaultBrowser) })
            NavItem(AppIcons.Palette, stringResource(Res.string.section_appearance),
                active = activeSection == NavSection.Appearance,
                onClick = { onSelect(NavSection.Appearance) })
            NavItem(AppIcons.Translate, stringResource(Res.string.section_language),
                active = activeSection == NavSection.Language,
                onClick = { onSelect(NavSection.Language) })
            NavItem(AppIcons.SettingsSuggest, stringResource(Res.string.section_system),
                active = activeSection == NavSection.System,
                onClick = { onSelect(NavSection.System) })
            NavItem(AppIcons.Settings, stringResource(Res.string.section_browser_exclusions),
                active = activeSection == NavSection.Exclusions,
                onClick = { onSelect(NavSection.Exclusions) })
            NavItem(AppIcons.SettingsSuggest, stringResource(Res.string.section_rules),
                active = activeSection == NavSection.Rules,
                onClick = { onSelect(NavSection.Rules) })
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val activeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    val activeFg = MaterialTheme.colorScheme.primary
    val inactiveFg = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (active) activeBg else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) activeFg else inactiveFg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (active) activeFg else inactiveFg,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(borderColor),
        )
    }
}

// endregion

// region section scaffolding

@Composable
private fun SectionPane(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = surfaceContainerLowest(),
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
    ) {
        content()
    }
}

// endregion

// region Default browser section

@Composable
private fun DefaultBrowserSection(
    currentOs: HostOs,
    isDefault: Boolean,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    SectionPane(stringResource(Res.string.section_default_browser), AppIcons.BrowserUpdated) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isPositive = isDefault)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isDefault) {
                        stringResource(Res.string.default_browser_status_yes)
                    } else {
                        stringResource(Res.string.default_browser_status_no)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.default_browser_instructions_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                defaultBrowserInstructions(currentOs).forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (canOpenSettings) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(stringResource(Res.string.default_browser_open_system_settings))
                    }
                }
            }
        }

        Text(
            text = stringResource(Res.string.default_browser_packaging_note),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(isPositive: Boolean) {
    val color = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color = color, shape = RoundedCornerShape(50)),
    )
}

// endregion

// region Appearance / Language / System sections

@Composable
private fun AppearanceSection(
    current: AppTheme,
    onSelected: (AppTheme) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_appearance), AppIcons.Palette) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.theme_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppTheme.entries,
                    optionLabel = { themeLabel(it) },
                    onSelected = onSelected,
                )
            }
        }
    }
}

@Composable
private fun LanguageSection(
    current: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_language), AppIcons.Translate) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppLanguage.entries,
                    optionLabel = { languageLabel(it) },
                    onSelected = onSelected,
                )
            }
        }
    }
}

@Composable
private fun SystemSection(
    autoStart: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_system), AppIcons.SettingsSuggest) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.start_at_login),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.start_at_login_description),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = autoStart,
                    onCheckedChange = onChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
    }
}

// endregion

// region Exclusions section (real data)

@Composable
private fun ExclusionsSection(
    browsersState: BrowsersState,
    excluded: Set<BrowserId>,
    manualBrowserIds: Set<BrowserId>,
    manualAddNotice: ManualAddNotice?,
    onToggle: (BrowserId, Boolean) -> Unit,
    onMoveUp: (BrowserId) -> Unit,
    onMoveDown: (BrowserId) -> Unit,
    onRemoveManual: (BrowserId) -> Unit,
    onAddBrowserClick: () -> Unit,
    onDismissManualAddNotice: () -> Unit,
    onRetry: () -> Unit,
) {
    SectionPane(stringResource(Res.string.section_browser_exclusions), AppIcons.Settings) {
        if (manualAddNotice != null) {
            ManualAddNoticeBanner(
                notice = manualAddNotice,
                onDismiss = onDismissManualAddNotice,
            )
        }
        when (val s = browsersState) {
            BrowsersState.Loading -> LoadingCard()
            is BrowsersState.Loaded -> BrowserList(
                browsers = s.browsers,
                excluded = excluded,
                manualBrowserIds = manualBrowserIds,
                onToggle = onToggle,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onRemoveManual = onRemoveManual,
                onAddBrowserClick = onAddBrowserClick,
            )
            is BrowsersState.Error -> ErrorCard(s.message, onRetry)
        }
    }
}

@Composable
private fun ManualAddNoticeBanner(
    notice: ManualAddNotice,
    onDismiss: () -> Unit,
) {
    val message = when (notice) {
        ManualAddNotice.Duplicate -> stringResource(Res.string.manual_add_duplicate)
        ManualAddNotice.IsSelf -> stringResource(Res.string.manual_add_self)
        is ManualAddNotice.InvalidApp ->
            stringResource(Res.string.manual_add_invalid_prefix) + notice.reason
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(Res.string.manual_add_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.browsers_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCard() {
    SectionCard {
        Text(
            text = stringResource(Res.string.browsers_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.browsers_error_prefix) + message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(6.dp)) {
                Text(stringResource(Res.string.retryStr))
            }
        }
    }
}

@Composable
private fun BrowserList(
    browsers: List<Browser>,
    excluded: Set<BrowserId>,
    manualBrowserIds: Set<BrowserId>,
    onToggle: (BrowserId, Boolean) -> Unit,
    onMoveUp: (BrowserId) -> Unit,
    onMoveDown: (BrowserId) -> Unit,
    onRemoveManual: (BrowserId) -> Unit,
    onAddBrowserClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(query, browsers) {
        if (query.isBlank()) browsers
        else browsers.filter { it.uiLabel.contains(query, ignoreCase = true) }
    }
    // Up/down operate on the unfiltered ordered list — searching narrows the
    // visible rows but reorder buttons still walk the full list, so disable
    // them when a query is active to avoid surprising "move past hidden row"
    // jumps. The buttons re-enable as soon as the search clears.
    val reorderEnabled = query.isBlank()

    Column {
        SearchField(
            query = query,
            onChange = { query = it },
            placeholder = stringResource(Res.string.search_browsers),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAddBrowserClick,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.add_browser),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = surfaceContainerLowest(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.browsers_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = surfaceContainerLowest(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                visible.forEachIndexed { index, browser ->
                    val id = browser.toBrowserId()
                    val absoluteIndex = browsers.indexOf(browser)
                    BrowserRow(
                        browser = browser,
                        isExcluded = id in excluded,
                        isManual = id in manualBrowserIds,
                        canMoveUp = reorderEnabled && absoluteIndex > 0,
                        canMoveDown = reorderEnabled && absoluteIndex < browsers.lastIndex,
                        onToggle = { newValue -> onToggle(id, newValue) },
                        onMoveUp = { onMoveUp(id) },
                        onMoveDown = { onMoveDown(id) },
                        onRemoveManual = { onRemoveManual(id) },
                    )
                    if (index != visible.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(
    browser: Browser,
    isExcluded: Boolean,
    isManual: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemoveManual: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isExcluded) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrowserIconBox(initial = browser.displayName.firstOrNull()?.uppercase() ?: "?")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = browser.uiLabel + (browser.version?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isExcluded) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = browser.bundleId,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = AppIcons.ArrowUp,
                contentDescription = stringResource(Res.string.move_up),
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = AppIcons.ArrowDown,
                contentDescription = stringResource(Res.string.move_down),
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        if (isManual) {
            IconButton(onClick = onRemoveManual) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(Res.string.remove_manual_browser),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (isExcluded) {
                stringResource(Res.string.excludedStr)
            } else {
                stringResource(Res.string.includedStr)
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isExcluded) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

// region Rules section (stage 6)

@Composable
private fun RulesSection(
    rules: List<UrlRule>,
    availableBrowsers: List<Browser>,
    onAddRule: (pattern: String, browserId: BrowserId) -> Unit,
    onRemoveRule: (index: Int) -> Unit,
    onMoveRule: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateRulePattern: (index: Int, pattern: String) -> Unit,
    onUpdateRuleBrowser: (index: Int, browserId: BrowserId) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_rules), AppIcons.SettingsSuggest) {
        if (availableBrowsers.isEmpty()) {
            SectionCard {
                Text(
                    text = stringResource(Res.string.rule_no_browsers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SectionPane
        }
        if (rules.isEmpty()) {
            SectionCard {
                Text(
                    text = stringResource(Res.string.rule_no_rules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = surfaceContainerLowest(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rules.forEachIndexed { index, rule ->
                    RuleRow(
                        rule = rule,
                        availableBrowsers = availableBrowsers,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onPatternChange = { onUpdateRulePattern(index, it) },
                        onBrowserChange = { onUpdateRuleBrowser(index, it) },
                        onMoveUp = { onMoveRule(index, index - 1) },
                        onMoveDown = { onMoveRule(index, index + 1) },
                        onRemove = { onRemoveRule(index) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = {
                // Default to the first available browser; user can pick another in the dropdown.
                val firstBrowser = availableBrowsers.first().toBrowserId()
                onAddRule("", firstBrowser)
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.add_rule),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: UrlRule,
    availableBrowsers: List<Browser>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPatternChange: (String) -> Unit,
    onBrowserChange: (BrowserId) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pattern field
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = surfaceContainerLow(),
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = rule.pattern,
                onValueChange = onPatternChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (rule.pattern.isEmpty()) {
                Text(
                    text = stringResource(Res.string.rule_pattern_placeholder),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // Browser dropdown
        BrowserDropdown(
            current = rule.browserId,
            options = availableBrowsers,
            onSelected = onBrowserChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = AppIcons.ArrowUp,
                contentDescription = stringResource(Res.string.move_up),
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = AppIcons.ArrowDown,
                contentDescription = stringResource(Res.string.move_down),
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = stringResource(Res.string.rule_remove_content_description),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun BrowserDropdown(
    current: BrowserId,
    options: List<Browser>,
    onSelected: (BrowserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentBrowser = options.firstOrNull { it.toBrowserId() == current }
    val label = currentBrowser?.uiLabel ?: current.value.substringAfterLast('/')
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = LocalContentColor.current,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { browser ->
                DropdownMenuItem(
                    text = { Text(browser.uiLabel) },
                    onClick = {
                        onSelected(browser.toBrowserId())
                        expanded = false
                    },
                )
            }
        }
    }
}

// endregion

@Composable
private fun BrowserIconBox(initial: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = surfaceContainerLowest(),
                shape = RoundedCornerShape(10.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// endregion

// region shared dropdown + tonal helpers

@Composable
private fun <T> EnumDropdown(
    value: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = optionLabel(value),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun surfaceContainerLow(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLow else LightSurfaceContainerLow

@Composable
private fun surfaceContainerLowest(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLowest else LightSurfaceContainerLowest

// endregion
