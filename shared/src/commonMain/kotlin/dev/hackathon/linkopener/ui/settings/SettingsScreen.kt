package dev.hackathon.linkopener.ui.settings

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.hackathon.linkopener.core.model.AppSettings
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.strings.Strings
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LocalIsDarkMode

private enum class NavSection { DefaultBrowser, Appearance, Language, System, Exclusions }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    strings: Strings,
    appVersion: String,
    currentOs: HostOs,
    appIconPainter: Painter? = null,
    onCloseRequest: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val browsers by viewModel.browsers.collectAsState()
    val isDefault by viewModel.isDefaultBrowser.collectAsState()
    var activeSection by remember { mutableStateOf(NavSection.DefaultBrowser) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                strings = strings,
                appIconPainter = appIconPainter,
                onCloseRequest = onCloseRequest,
            )
            if (!isDefault) {
                NotDefaultBanner(
                    strings = strings,
                    canOpenSettings = viewModel.canOpenSystemSettings,
                    onOpenSettings = viewModel::openSystemSettings,
                    onSelectDefaultSection = { activeSection = NavSection.DefaultBrowser },
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    strings = strings,
                    appVersion = appVersion,
                    activeSection = activeSection,
                    onSelect = { activeSection = it },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Crossfade(targetState = activeSection) { section ->
                        when (section) {
                            NavSection.DefaultBrowser -> DefaultBrowserSection(
                                strings = strings,
                                currentOs = currentOs,
                                isDefault = isDefault,
                                canOpenSettings = viewModel.canOpenSystemSettings,
                                onOpenSettings = viewModel::openSystemSettings,
                            )
                            NavSection.Appearance -> AppearanceSection(strings, settings.theme, viewModel::onThemeSelected)
                            NavSection.Language -> LanguageSection(strings, settings.language, viewModel::onLanguageSelected)
                            NavSection.System -> SystemSection(strings, settings.autoStartEnabled, viewModel::onAutoStartChanged)
                            NavSection.Exclusions -> ExclusionsSection(
                                strings = strings,
                                browsersState = browsers,
                                excluded = settings.excludedBrowserIds,
                                onToggle = viewModel::onBrowserExclusionToggled,
                                onRetry = viewModel::refreshBrowsers,
                            )
                        }
                    }
                }
            }
        }
    }
}

// region top bar + banner

@Composable
private fun TopAppBar(
    strings: Strings,
    appIconPainter: Painter?,
    onCloseRequest: () -> Unit,
) {
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
                        contentDescription = strings.appName,
                        tint = Color.Unspecified,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* TODO: help action */ }) {
                Icon(
                    imageVector = AppIcons.Help,
                    contentDescription = strings.help,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCloseRequest) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = strings.close,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NotDefaultBanner(
    strings: Strings,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
    onSelectDefaultSection: () -> Unit,
) {
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
                    text = strings.bannerNotDefaultTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = strings.bannerNotDefaultBody,
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
                    Text(strings.bannerOpenSettings)
                }
            }
        }
    }
}

// endregion

// region sidebar

@Composable
private fun Sidebar(
    strings: Strings,
    appVersion: String,
    activeSection: NavSection,
    onSelect: (NavSection) -> Unit,
) {
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
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = strings.versionPrefix + appVersion,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NavItem(AppIcons.BrowserUpdated, strings.sectionDefaultBrowser,
                active = activeSection == NavSection.DefaultBrowser,
                onClick = { onSelect(NavSection.DefaultBrowser) })
            NavItem(AppIcons.Palette, strings.sectionAppearance,
                active = activeSection == NavSection.Appearance,
                onClick = { onSelect(NavSection.Appearance) })
            NavItem(AppIcons.Translate, strings.sectionLanguage,
                active = activeSection == NavSection.Language,
                onClick = { onSelect(NavSection.Language) })
            NavItem(AppIcons.SettingsSuggest, strings.sectionSystem,
                active = activeSection == NavSection.System,
                onClick = { onSelect(NavSection.System) })
            NavItem(AppIcons.Settings, strings.sectionBrowserExclusions,
                active = activeSection == NavSection.Exclusions,
                onClick = { onSelect(NavSection.Exclusions) })
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
    strings: Strings,
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
                // strings keeps the scope; explicit reference avoids unused warning when sections don't use it directly.
                @Suppress("UNUSED_EXPRESSION") strings
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
    strings: Strings,
    currentOs: HostOs,
    isDefault: Boolean,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    SectionPane(strings, strings.sectionDefaultBrowser, AppIcons.BrowserUpdated) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isPositive = isDefault)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isDefault) strings.defaultBrowserStatusYes else strings.defaultBrowserStatusNo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.defaultBrowserInstructionsHeader,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                strings.defaultBrowserInstructions(currentOs).forEachIndexed { index, step ->
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
                        Text(strings.defaultBrowserOpenSystemSettings)
                    }
                }
            }
        }

        Text(
            text = strings.defaultBrowserPackagingNote,
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
    strings: Strings,
    current: AppTheme,
    onSelected: (AppTheme) -> Unit,
) {
    SectionPane(strings, strings.sectionAppearance, AppIcons.Palette) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.themeMode,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppTheme.entries,
                    optionLabel = strings::label,
                    onSelected = onSelected,
                )
            }
        }
    }
}

@Composable
private fun LanguageSection(
    strings: Strings,
    current: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    SectionPane(strings, strings.sectionLanguage, AppIcons.Translate) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.appLanguage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppLanguage.entries,
                    optionLabel = strings::label,
                    onSelected = onSelected,
                )
            }
        }
    }
}

@Composable
private fun SystemSection(
    strings: Strings,
    autoStart: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SectionPane(strings, strings.sectionSystem, AppIcons.SettingsSuggest) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.startAtLogin,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = strings.startAtLoginDescription,
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
    strings: Strings,
    browsersState: BrowsersState,
    excluded: Set<BrowserId>,
    onToggle: (BrowserId, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    SectionPane(strings, strings.sectionBrowserExclusions, AppIcons.Settings) {
        when (val s = browsersState) {
            BrowsersState.Loading -> LoadingCard(strings)
            is BrowsersState.Loaded -> if (s.browsers.isEmpty()) {
                EmptyCard(strings)
            } else {
                BrowserList(
                    browsers = s.browsers,
                    excluded = excluded,
                    strings = strings,
                    onToggle = onToggle,
                )
            }
            is BrowsersState.Error -> ErrorCard(strings, s.message, onRetry)
        }
    }
}

@Composable
private fun LoadingCard(strings: Strings) {
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
                text = strings.browsersLoading,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCard(strings: Strings) {
    SectionCard {
        Text(
            text = strings.browsersEmpty,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(strings: Strings, message: String, onRetry: () -> Unit) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = strings.browsersErrorPrefix + message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(6.dp)) {
                Text(strings.retry)
            }
        }
    }
}

@Composable
private fun BrowserList(
    browsers: List<Browser>,
    excluded: Set<BrowserId>,
    strings: Strings,
    onToggle: (BrowserId, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(query, browsers) {
        if (query.isBlank()) browsers
        else browsers.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    Column {
        SearchField(query = query, onChange = { query = it }, placeholder = strings.searchBrowsers)
        Spacer(Modifier.height(12.dp))
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
                val id = BrowserId(browser.bundleId)
                BrowserRow(
                    browser = browser,
                    isExcluded = id in excluded,
                    strings = strings,
                    onToggle = { newValue -> onToggle(id, newValue) },
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

@Composable
private fun BrowserRow(
    browser: Browser,
    isExcluded: Boolean,
    strings: Strings,
    onToggle: (Boolean) -> Unit,
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
                text = browser.displayName + (browser.version?.let { " $it" } ?: ""),
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
        Text(
            text = if (isExcluded) strings.excluded else strings.included,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isExcluded) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

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
    optionLabel: (T) -> String,
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
