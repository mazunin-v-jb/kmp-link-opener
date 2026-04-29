package dev.hackathon.linkopener.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.NavSection
import dev.hackathon.linkopener.ui.strings.useLocaleNonce
import dev.hackathon.linkopener.ui.theme.surfaceContainerLow
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.section_appearance
import kmp_link_opener.shared.generated.resources.section_browser_exclusions
import kmp_link_opener.shared.generated.resources.section_default_browser
import kmp_link_opener.shared.generated.resources.section_language
import kmp_link_opener.shared.generated.resources.section_rules
import kmp_link_opener.shared.generated.resources.section_system
import kmp_link_opener.shared.generated.resources.settings_title
import kmp_link_opener.shared.generated.resources.version_prefix
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun Sidebar(
    appVersion: String,
    activeSection: NavSection,
    onSelect: (NavSection) -> Unit,
) {
    useLocaleNonce()
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

            NavItem(
                AppIcons.BrowserUpdated, stringResource(Res.string.section_default_browser),
                active = activeSection == NavSection.DefaultBrowser,
                onClick = { onSelect(NavSection.DefaultBrowser) },
            )
            NavItem(
                AppIcons.Palette, stringResource(Res.string.section_appearance),
                active = activeSection == NavSection.Appearance,
                onClick = { onSelect(NavSection.Appearance) },
            )
            NavItem(
                AppIcons.Translate, stringResource(Res.string.section_language),
                active = activeSection == NavSection.Language,
                onClick = { onSelect(NavSection.Language) },
            )
            NavItem(
                AppIcons.SettingsSuggest, stringResource(Res.string.section_system),
                active = activeSection == NavSection.System,
                onClick = { onSelect(NavSection.System) },
            )
            NavItem(
                AppIcons.Settings, stringResource(Res.string.section_browser_exclusions),
                active = activeSection == NavSection.Exclusions,
                onClick = { onSelect(NavSection.Exclusions) },
            )
            NavItem(
                AppIcons.SettingsSuggest, stringResource(Res.string.section_rules),
                active = activeSection == NavSection.Rules,
                onClick = { onSelect(NavSection.Rules) },
            )
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
