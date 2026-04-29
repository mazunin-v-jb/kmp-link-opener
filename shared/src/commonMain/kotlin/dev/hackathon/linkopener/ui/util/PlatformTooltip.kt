package dev.hackathon.linkopener.ui.util

import androidx.compose.runtime.Composable

/**
 * Wraps [content] with a hover-tooltip showing [text].
 *
 * Desktop: renders Compose Desktop's `TooltipArea` with a styled surface.
 * Android: just emits content — touch UIs don't have hover, and the
 *          actions wrapped here also have visible icons / labels.
 */
@Composable
expect fun PlatformTooltip(
    text: String,
    content: @Composable () -> Unit,
)
