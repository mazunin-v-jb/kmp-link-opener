package dev.hackathon.linkopener.ui.settings.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.NavSection
import dev.hackathon.linkopener.ui.strings.useLocaleNonce
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.section_appearance
import kmp_link_opener.shared.generated.resources.section_browser_exclusions
import kmp_link_opener.shared.generated.resources.section_default_browser
import kmp_link_opener.shared.generated.resources.section_language
import kmp_link_opener.shared.generated.resources.section_rules
import kmp_link_opener.shared.generated.resources.section_system
import org.jetbrains.compose.resources.stringResource

/**
 * Mobile-form alternative to [Sidebar]. Used by [SettingsScreen] when the
 * window is narrow enough that a 240dp side panel would leave the content
 * area unusable. Same `(activeSection, onSelect)` contract.
 *
 * Six sections at NavigationBar's `alwaysShowLabel = false` mode keep the
 * bar short on phones — labels appear only under the selected item.
 */
@Composable
internal fun BottomNavBar(
    activeSection: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    useLocaleNonce()
    NavigationBar(modifier = modifier) {
        NavBarItem(
            icon = AppIcons.DefaultBrowser,
            label = stringResource(Res.string.section_default_browser),
            active = activeSection == NavSection.DefaultBrowser,
            onClick = { onSelect(NavSection.DefaultBrowser) },
        )
        NavBarItem(
            icon = AppIcons.Appearance,
            label = stringResource(Res.string.section_appearance),
            active = activeSection == NavSection.Appearance,
            onClick = { onSelect(NavSection.Appearance) },
        )
        NavBarItem(
            icon = AppIcons.Language,
            label = stringResource(Res.string.section_language),
            active = activeSection == NavSection.Language,
            onClick = { onSelect(NavSection.Language) },
        )
        NavBarItem(
            icon = AppIcons.System,
            label = stringResource(Res.string.section_system),
            active = activeSection == NavSection.System,
            onClick = { onSelect(NavSection.System) },
        )
        NavBarItem(
            icon = AppIcons.Rules,
            label = stringResource(Res.string.section_rules),
            active = activeSection == NavSection.Rules,
            onClick = { onSelect(NavSection.Rules) },
        )
        NavBarItem(
            icon = AppIcons.BrowserExclusions,
            label = stringResource(Res.string.section_browser_exclusions),
            active = activeSection == NavSection.Exclusions,
            onClick = { onSelect(NavSection.Exclusions) },
        )
    }
}

@Composable
private fun RowScope.NavBarItem(
    icon: Painter,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = active,
        onClick = onClick,
        icon = {
            Icon(
                painter = icon,
                contentDescription = label,
                modifier = Modifier.padding(2.dp),
            )
        },
        label = { androidx.compose.material3.Text(label, maxLines = 1) },
        alwaysShowLabel = false,
    )
}
