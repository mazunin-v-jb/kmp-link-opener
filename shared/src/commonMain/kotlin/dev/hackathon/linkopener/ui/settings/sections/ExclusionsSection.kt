package dev.hackathon.linkopener.ui.settings.sections

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.core.model.uiLabel
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.BrowsersState
import dev.hackathon.linkopener.ui.settings.ManualAddNotice
import dev.hackathon.linkopener.ui.settings.components.SearchField
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import dev.hackathon.linkopener.ui.theme.BrowserAvatar
import dev.hackathon.linkopener.ui.theme.surfaceContainerLowest
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.add_browser
import kmp_link_opener.shared.generated.resources.browsers_empty
import kmp_link_opener.shared.generated.resources.browsers_error_prefix
import kmp_link_opener.shared.generated.resources.browsers_loading
import kmp_link_opener.shared.generated.resources.excluded as excludedStr
import kmp_link_opener.shared.generated.resources.included as includedStr
import kmp_link_opener.shared.generated.resources.manual_add_dismiss
import kmp_link_opener.shared.generated.resources.manual_add_duplicate
import kmp_link_opener.shared.generated.resources.manual_add_invalid_prefix
import kmp_link_opener.shared.generated.resources.manual_add_self
import kmp_link_opener.shared.generated.resources.move_down
import kmp_link_opener.shared.generated.resources.move_up
import kmp_link_opener.shared.generated.resources.remove_manual_browser
import kmp_link_opener.shared.generated.resources.retry as retryStr
import kmp_link_opener.shared.generated.resources.search_browsers
import kmp_link_opener.shared.generated.resources.section_browser_exclusions
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExclusionsSection(
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
        BrowserAvatar(initial = browser.displayName.firstOrNull()?.uppercase() ?: "?")
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
