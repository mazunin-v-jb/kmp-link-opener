package dev.hackathon.linkopener.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.DarkSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLow
import dev.hackathon.linkopener.ui.theme.LightSurfaceContainerLowest
import dev.hackathon.linkopener.ui.theme.LocalIsDarkMode
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.picker_empty
import kmp_link_opener.shared.generated.resources.picker_empty_hint
import kmp_link_opener.shared.generated.resources.picker_header_open
import kmp_link_opener.shared.generated.resources.picker_show_all
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_VISIBLE_COUNT = 3

@Composable
fun BrowserPickerScreen(
    url: String,
    browsers: List<Browser>,
    onPick: (Browser) -> Unit,
) {
    var expanded by remember(browsers) { mutableStateOf(false) }
    val visible = if (expanded || browsers.size <= DEFAULT_VISIBLE_COUNT) browsers
    else browsers.take(DEFAULT_VISIBLE_COUNT)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceContainerLow(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Header(url = url)

            if (browsers.isEmpty()) {
                EmptyState()
            } else {
                visible.forEach { browser ->
                    BrowserRow(browser = browser, onClick = { onPick(browser) })
                }
                if (!expanded && browsers.size > DEFAULT_VISIBLE_COUNT) {
                    ShowAllButton(
                        label = "${stringResource(Res.string.picker_show_all)} (${browsers.size})",
                        onClick = { expanded = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(url: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(Res.string.picker_header_open),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = url,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

@Composable
private fun BrowserRow(browser: Browser, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(initial = browser.displayName.firstOrNull()?.uppercase() ?: "?")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = browser.displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (browser.version != null) {
                Text(
                    text = browser.version,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun IconBox(initial: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ShowAllButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.picker_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.picker_empty_hint),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun surfaceContainerLow(): androidx.compose.ui.graphics.Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLow else LightSurfaceContainerLow

@Suppress("unused")
@Composable
private fun surfaceContainerLowest(): androidx.compose.ui.graphics.Color =
    if (LocalIsDarkMode.current) DarkSurfaceContainerLowest else LightSurfaceContainerLowest
