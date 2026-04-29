package dev.hackathon.linkopener.ui.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.strings.useLocaleNonce
import dev.hackathon.linkopener.ui.theme.surfaceContainerLow
import dev.hackathon.linkopener.ui.util.PlatformTooltip
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_name
import kmp_link_opener.shared.generated.resources.tooltip_close
import kmp_link_opener.shared.generated.resources.tooltip_help
import kmp_link_opener.shared.generated.resources.tooltip_help_easter
import kmp_link_opener.shared.generated.resources.tooltip_refresh
import kmp_link_opener.shared.generated.resources.tooltip_refresh_easter
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

// Probability of swapping a tooltip's normal label for its meme variant.
// Rolled once per Settings open (see `remember { … }` in the body), so the
// label stays consistent across hovers within a single session.
private const val EASTER_EGG_PROBABILITY = 0.05f

@Composable
internal fun SettingsTopAppBar(
    onRefresh: () -> Unit,
    onCloseRequest: () -> Unit,
    showCloseButton: Boolean = true,
) {
    useLocaleNonce()
    val appName = stringResource(Res.string.app_name)

    // Roll the dice once per composition entry — i.e. per "open Settings"
    // — so a session that opened with the meme keeps showing the meme
    // until the user closes + reopens. The `remember` key has no inputs,
    // so the value survives recompositions inside the open session.
    val showRefreshEaster = remember { Random.nextFloat() < EASTER_EGG_PROBABILITY }
    val showHelpEaster = remember { Random.nextFloat() < EASTER_EGG_PROBABILITY }

    val refreshTooltip = if (showRefreshEaster) {
        stringResource(Res.string.tooltip_refresh_easter)
    } else {
        stringResource(Res.string.tooltip_refresh)
    }
    val helpTooltip = if (showHelpEaster) {
        stringResource(Res.string.tooltip_help_easter)
    } else {
        stringResource(Res.string.tooltip_help)
    }
    val closeTooltip = stringResource(Res.string.tooltip_close)

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
            Box(modifier = Modifier.size(24.dp)) {
                Icon(
                    painter = AppIcons.AppLogoV2,
                    contentDescription = appName,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            TopBarTooltip(text = refreshTooltip) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        painter = AppIcons.Reload,
                        contentDescription = refreshTooltip,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TopBarTooltip(text = helpTooltip) {
                IconButton(onClick = { /* TODO: help action */ }) {
                    Icon(
                        painter = AppIcons.Help,
                        contentDescription = helpTooltip,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showCloseButton) {
                TopBarTooltip(text = closeTooltip) {
                    IconButton(onClick = onCloseRequest) {
                        Icon(
                            painter = AppIcons.Close,
                            contentDescription = closeTooltip,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wraps an icon-button in a hover tooltip on desktop; on Android the
 * tooltip is dropped (touch UIs have no hover) and we just emit content.
 * The actual rendering lives in `PlatformTooltip` actuals.
 */
@Composable
private fun TopBarTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    PlatformTooltip(text = text, content = content)
}
