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
import androidx.compose.ui.graphics.ImageBitmap
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
import sh.calvin.reorderable.ReorderableColumn
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
import kmp_link_opener.shared.generated.resources.remove_manual_browser
import kmp_link_opener.shared.generated.resources.tooltip_drag_to_reorder
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
    icons: Map<String, ImageBitmap>,
    onToggle: (BrowserId, Boolean) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveManual: (BrowserId) -> Unit,
    onAddBrowserClick: () -> Unit,
    onDismissManualAddNotice: () -> Unit,
    onRetry: () -> Unit,
) {
    SectionPane(stringResource(Res.string.section_browser_exclusions), AppIcons.BrowserExclusions) {
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
                icons = icons,
                onToggle = onToggle,
                onReorder = onReorder,
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
                    painter = AppIcons.Close,
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
    icons: Map<String, ImageBitmap>,
    onToggle: (BrowserId, Boolean) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveManual: (BrowserId) -> Unit,
    onAddBrowserClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(query, browsers) {
        if (query.isBlank()) browsers
        else browsers.filter { it.uiLabel.contains(query, ignoreCase = true) }
    }
    // Drag-to-reorder operates on absolute indices in the full unfiltered
    // list. When a query narrows the visible rows, drag is disabled because
    // dropping at "visible position 3" would map to the wrong absolute slot
    // in the underlying list. The drag affordance hides until the search
    // clears.
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
            SectionCard {
                Text(
                    text = stringResource(Res.string.browsers_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            BrowserListContainer {
                if (reorderEnabled) {
                    // Calvin-LL/Reorderable's `ReorderableColumn` is a regular
                    // (non-Lazy) Column that gives each child a draggable
                    // handle modifier and animates row positions on drop.
                    // `onSettle` fires after the gesture completes — we use it
                    // for persistence, mirroring the docs' recommendation.
                    // `Modifier.draggableHandle()` is an extension on the
                    // `ReorderableColumnScope` that the content lambda
                    // receives, so we resolve it here and pass it down — the
                    // inner row composable doesn't carry that scope.
                    ReorderableColumn(
                        list = visible,
                        onSettle = onReorder,
                    ) { index, browser, _ ->
                        // `ReorderableItem` is the wrapper that exposes the
                        // `ReorderableListItemScope`, where
                        // `Modifier.draggableHandle()` lives. We resolve the
                        // modifier here, then pass the plain `Modifier` value
                        // down to `BrowserRow` (no scope leak required).
                        ReorderableItem {
                            val id = browser.toBrowserId()
                            BrowserRow(
                                browser = browser,
                                icon = icons[browser.applicationPath],
                                isExcluded = id in excluded,
                                isManual = id in manualBrowserIds,
                                dragHandleModifier = Modifier.draggableHandle(),
                                showDivider = index != visible.lastIndex,
                                onToggle = { newValue -> onToggle(id, newValue) },
                                onRemoveManual = { onRemoveManual(id) },
                            )
                        }
                    }
                } else {
                    visible.forEachIndexed { index, browser ->
                        val id = browser.toBrowserId()
                        BrowserRow(
                            browser = browser,
                            icon = icons[browser.applicationPath],
                            isExcluded = id in excluded,
                            isManual = id in manualBrowserIds,
                            // No drag during search — the absolute-index
                            // mapping would be ambiguous on a filtered view.
                            dragHandleModifier = null,
                            showDivider = index != visible.lastIndex,
                            onToggle = { newValue -> onToggle(id, newValue) },
                            onRemoveManual = { onRemoveManual(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserListContainer(content: @Composable () -> Unit) {
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
        content()
    }
}

@Composable
private fun DragHandleAffordance(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.DragHandle,
            contentDescription = stringResource(Res.string.tooltip_drag_to_reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BrowserRow(
    browser: Browser,
    icon: ImageBitmap?,
    isExcluded: Boolean,
    isManual: Boolean,
    // Pre-resolved `Modifier.draggableHandle()` from `ReorderableColumnScope`.
    // `null` hides the drag affordance (used while the search filter is
    // active — dropping at "visible position 3" would map ambiguously back
    // to the unfiltered list, so we suppress drag rather than guess).
    dragHandleModifier: Modifier?,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemoveManual: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!isExcluded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragHandleModifier != null) {
                DragHandleAffordance(modifier = dragHandleModifier)
                Spacer(Modifier.width(8.dp))
            }
            BrowserAvatar(
                initial = browser.displayName.firstOrNull()?.uppercase() ?: "?",
                icon = icon,
            )
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
            if (isManual) {
                IconButton(onClick = onRemoveManual) {
                    Icon(
                        painter = AppIcons.Close,
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
        if (showDivider) {
            // Divider lives inside each row (instead of between rows in the
            // parent Column) so `ReorderableColumn` doesn't see separator
            // boxes as draggable items — its `list` parameter is the
            // browsers, and only the row composable wraps each entry.
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
