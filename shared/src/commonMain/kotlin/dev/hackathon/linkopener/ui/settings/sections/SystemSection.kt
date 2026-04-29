package dev.hackathon.linkopener.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.section_system
import kmp_link_opener.shared.generated.resources.show_browser_profiles_description
import kmp_link_opener.shared.generated.resources.show_browser_profiles_title
import kmp_link_opener.shared.generated.resources.start_at_login
import kmp_link_opener.shared.generated.resources.start_at_login_description
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SystemSection(
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    showBrowserProfiles: Boolean,
    onShowBrowserProfilesChange: (Boolean) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_system), AppIcons.System) {
        SectionCard {
            ToggleRow(
                title = stringResource(Res.string.start_at_login),
                description = stringResource(Res.string.start_at_login_description),
                checked = autoStart,
                onCheckedChange = onAutoStartChange,
            )
        }
        SectionCard {
            ToggleRow(
                title = stringResource(Res.string.show_browser_profiles_title),
                description = stringResource(Res.string.show_browser_profiles_description),
                checked = showBrowserProfiles,
                onCheckedChange = onShowBrowserProfilesChange,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

