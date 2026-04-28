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
import androidx.compose.ui.draw.clip
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
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.strings.Strings
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LocalIsDarkMode

private enum class NavSection { Appearance, Language, System, Exclusions }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    strings: Strings,
    appVersion: String,
    appIconPainter: Painter? = null,
    onCloseRequest: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    var activeSection by remember { mutableStateOf(NavSection.Appearance) }

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
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(
                    strings = strings,
                    appVersion = appVersion,
                    activeSection = activeSection,
                    onSelect = { activeSection = it },
                )
                MainContent(
                    strings = strings,
                    settings = settings,
                    onThemeSelected = viewModel::onThemeSelected,
                    onLanguageSelected = viewModel::onLanguageSelected,
                    onAutoStartChanged = viewModel::onAutoStartChanged,
                    onExclusionToggled = viewModel::onBrowserExclusionToggled,
                )
            }
        }
    }
}

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
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
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

            NavItem(AppIcons.Palette, strings.sectionAppearance,
                active = activeSection == NavSection.Appearance,
                onClick = { onSelect(NavSection.Appearance) })
            NavItem(AppIcons.Translate, strings.sectionLanguage,
                active = activeSection == NavSection.Language,
                onClick = { onSelect(NavSection.Language) })
            NavItem(AppIcons.SettingsSuggest, strings.sectionSystem,
                active = activeSection == NavSection.System,
                onClick = { onSelect(NavSection.System) })
            NavItem(AppIcons.BrowserUpdated, strings.sectionBrowserExclusions,
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

@Composable
private fun MainContent(
    strings: Strings,
    settings: AppSettings,
    onThemeSelected: (AppTheme) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onAutoStartChanged: (Boolean) -> Unit,
    onExclusionToggled: (BrowserId, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .wrapContentWidth(Alignment.CenterHorizontally),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                AppearanceSection(strings, settings.theme, onThemeSelected)
                LanguageSection(strings, settings.language, onLanguageSelected)
                SystemSection(strings, settings.autoStartEnabled, onAutoStartChanged)
                ExclusionsSection(strings, settings.excludedBrowserIds, onExclusionToggled)
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

@Composable
private fun AppearanceSection(
    strings: Strings,
    current: AppTheme,
    onSelected: (AppTheme) -> Unit,
) {
    Column {
        SectionHeader(AppIcons.Palette, strings.sectionAppearance)
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
    Column {
        SectionHeader(AppIcons.Translate, strings.sectionLanguage)
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
    Column {
        SectionHeader(AppIcons.SettingsSuggest, strings.sectionSystem)
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

@Composable
private fun ExclusionsSection(
    strings: Strings,
    excluded: Set<BrowserId>,
    onToggle: (BrowserId, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(query) {
        if (query.isBlank()) MockBrowsers
        else MockBrowsers.filter { it.displayName.contains(query, ignoreCase = true) }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.BrowserUpdated,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = strings.sectionBrowserExclusions,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.addBrowser,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { /* TODO: open file picker — stage 2 */ },
            )
        }

        SearchField(query = query, onChange = { query = it }, placeholder = strings.searchBrowsers)
        Spacer(Modifier.height(8.dp))

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
                BrowserRow(
                    browser = browser,
                    isExcluded = browser.id in excluded,
                    strings = strings,
                    onToggle = { newValue -> onToggle(browser.id, newValue) },
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
            if (visible.isEmpty()) {
                Text(
                    text = strings.emptyBrowsersMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BrowserRow(
    browser: MockBrowser,
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
        BrowserIconBox(browser = browser)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = browser.displayName,
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
            val secondary: String? = when {
                isExcluded -> strings.excluded
                browser.isSystemDefault -> strings.systemDefault
                browser.secondaryLabel != null -> browser.secondaryLabel
                else -> null
            }
            if (secondary != null) {
                val secondaryColor = if (isExcluded) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                }
                Text(
                    text = secondary.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = secondaryColor,
                )
            }
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
private fun BrowserIconBox(browser: MockBrowser) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = browser.accentBackground,
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = browser.initial,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = browser.accentForeground,
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

// surfaceContainer* roles aren't directly exposed on the M3 ColorScheme in
// 1.10.x, so we fall back to the design-system values keyed by dark-mode
// flag from LocalIsDarkMode.
@Composable
private fun surfaceContainerLow(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLow else LightSurfaceContainerLow

@Composable
private fun surfaceContainerLowest(): Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLowest else LightSurfaceContainerLowest
