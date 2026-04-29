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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.strings.useLocaleNonce
import dev.hackathon.linkopener.ui.theme.surfaceContainerLow
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_name
import kmp_link_opener.shared.generated.resources.close as closeStr
import kmp_link_opener.shared.generated.resources.help as helpStr
import kmp_link_opener.shared.generated.resources.refresh_action
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsTopAppBar(
    onRefresh: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    useLocaleNonce()
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
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = AppIcons.Reload,
                    contentDescription = stringResource(Res.string.refresh_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { /* TODO: help action */ }) {
                Icon(
                    painter = AppIcons.Help,
                    contentDescription = stringResource(Res.string.helpStr),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCloseRequest) {
                Icon(
                    painter = AppIcons.Close,
                    contentDescription = stringResource(Res.string.closeStr),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
