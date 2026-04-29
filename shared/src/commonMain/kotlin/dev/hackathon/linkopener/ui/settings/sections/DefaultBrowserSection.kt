package dev.hackathon.linkopener.ui.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import dev.hackathon.linkopener.ui.strings.defaultBrowserInstructions
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.default_browser_instructions_header
import kmp_link_opener.shared.generated.resources.default_browser_open_system_settings
import kmp_link_opener.shared.generated.resources.default_browser_packaging_note
import kmp_link_opener.shared.generated.resources.default_browser_refresh_hint
import kmp_link_opener.shared.generated.resources.default_browser_status_no
import kmp_link_opener.shared.generated.resources.default_browser_status_yes
import kmp_link_opener.shared.generated.resources.section_default_browser
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DefaultBrowserSection(
    currentOs: HostOs,
    isDefault: Boolean,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
) {
    SectionPane(stringResource(Res.string.section_default_browser), AppIcons.DefaultBrowser) {
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
                // Hint after the steps + button: macOS only flips the
                // default-browser indicator when the user actually picks one
                // in System Settings, and our WatchService against the
                // LaunchServices plist may miss an event during reconfigure.
                // The TopAppBar refresh button forces a re-read.
                Text(
                    text = stringResource(Res.string.default_browser_refresh_hint),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
