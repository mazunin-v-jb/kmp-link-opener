package dev.hackathon.linkopener.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import dev.hackathon.linkopener.ui.util.PlatformVerticalScrollbar
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.core.model.uiLabel
import dev.hackathon.linkopener.ui.strings.useLocaleNonce
import dev.hackathon.linkopener.ui.theme.BrowserAvatar
import dev.hackathon.linkopener.ui.theme.surfaceContainerLow
import dev.hackathon.linkopener.ui.util.PlatformTooltip
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.picker_browser_not_running
import kmp_link_opener.shared.generated.resources.picker_empty
import kmp_link_opener.shared.generated.resources.picker_empty_hint
import kmp_link_opener.shared.generated.resources.picker_header_open
import kmp_link_opener.shared.generated.resources.picker_show_all
import org.jetbrains.compose.resources.stringResource

private const val DEFAULT_VISIBLE_COUNT = 3

// "Show all" pulls the popup three more rows down so the user sees twice
// the original count without scrolling, with the rest reachable via scroll.
private const val EXPANDED_VISIBLE_COUNT = DEFAULT_VISIBLE_COUNT * 2

// Each BrowserRow is icon-bound (32dp icon + 10dp top + 10dp bottom padding = 52dp),
// so we can cap the expanded scroll viewport without measuring.
private val ROW_HEIGHT = 52.dp
private val EXPANDED_SCROLL_MAX_HEIGHT = ROW_HEIGHT * EXPANDED_VISIBLE_COUNT

@Composable
fun BrowserPickerScreen(
    url: String,
    browsers: List<Browser>,
    onPick: (Browser) -> Unit,
    onExpand: () -> Unit = {},
    // Keyed by Browser.applicationPath. Missing key → row falls back to the
    // letter avatar, matching the look before "browser-icons" landed.
    icons: Map<String, ImageBitmap> = emptyMap(),
    // Subset of [browsers] whose process is currently running; non-members
    // get rendered with reduced alpha. Three-state value, see PickerState
    // KDoc — `null` means "no probe info available" and keeps every row
    // opaque (Android, probe failure). Empty set means "probe ran, nothing
    // running" and fades every row.
    runningBrowserIds: Set<BrowserId>? = null,
    headerWrapper: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
    // Subscribe the picker subtree to the language nonce so a language
    // switch invalidates everything below — without this, Compose smart-
    // skips composables whose only string-typed inputs are stringResource
    // lookups, leaving the popup stuck on the previous language until
    // some other input changes (e.g. the user re-clicks). Same trick the
    // Settings tree uses (TopAppBar / Sidebar / NotDefaultBanner all read
    // useLocaleNonce()).
    useLocaleNonce()

    var expanded by remember(browsers) { mutableStateOf(false) }
    // null = host couldn't tell us (Android / probe failure) — leave every
    // row at full opacity. Empty set is meaningful: probe ran, nothing's
    // running, fade everyone.
    val noRunningInfo = runningBrowserIds == null
    val effectiveRunningIds = runningBrowserIds.orEmpty()

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
            headerWrapper {
                Header(url = url)
            }

            if (browsers.isEmpty()) {
                EmptyState()
            } else if (expanded) {
                ScrollableBrowserList(
                    browsers = browsers,
                    icons = icons,
                    runningBrowserIds = effectiveRunningIds,
                    noRunningInfo = noRunningInfo,
                    onPick = onPick,
                )
            } else {
                browsers.take(DEFAULT_VISIBLE_COUNT).forEach { browser ->
                    BrowserRow(
                        browser = browser,
                        icon = icons[browser.applicationPath],
                        isRunning = noRunningInfo || browser.toBrowserId() in effectiveRunningIds,
                        onClick = { onPick(browser) },
                    )
                }
                if (browsers.size > DEFAULT_VISIBLE_COUNT) {
                    ShowAllButton(
                        label = "${stringResource(Res.string.picker_show_all)} (${browsers.size})",
                        onClick = {
                            expanded = true
                            onExpand()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableBrowserList(
    browsers: List<Browser>,
    icons: Map<String, ImageBitmap>,
    runningBrowserIds: Set<BrowserId>,
    noRunningInfo: Boolean,
    onPick: (Browser) -> Unit,
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.heightIn(max = EXPANDED_SCROLL_MAX_HEIGHT)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            browsers.forEach { browser ->
                BrowserRow(
                    browser = browser,
                    icon = icons[browser.applicationPath],
                    isRunning = noRunningInfo || browser.toBrowserId() in runningBrowserIds,
                    onClick = { onPick(browser) },
                )
            }
        }
        PlatformVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
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
        // Cap the URL block at 3 lines so a giant URL can't blow up the
        // popup vertically; ellipsize anything longer. The full URL is
        // always reachable through the hover tooltip — cheap on desktop
        // (CMP TooltipArea), no-op on Android (no hover surface).
        PlatformTooltip(text = url) {
            Text(
                text = url,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

@Composable
private fun BrowserRow(
    browser: Browser,
    icon: ImageBitmap?,
    isRunning: Boolean,
    onClick: () -> Unit,
) {
    // Stopped browsers fade to 45% — clearly demoted but still legible.
    // Modifier.alpha is graphics-layer only, so the click target stays the
    // full row regardless of opacity.
    val rowAlpha = if (isRunning) 1f else 0.45f
    val notRunningLabel = stringResource(Res.string.picker_browser_not_running)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(rowAlpha)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrowserAvatar(
            initial = browser.displayName.firstOrNull()?.uppercase() ?: "?",
            bordered = true,
            icon = icon,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = browser.uiLabel,
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
            if (!isRunning) {
                Text(
                    text = notRunningLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
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

