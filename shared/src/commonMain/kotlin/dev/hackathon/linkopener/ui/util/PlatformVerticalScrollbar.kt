package dev.hackathon.linkopener.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a vertical scrollbar overlaid on a vertically-scrolling container.
 *
 * Desktop: thin draggable scrollbar (Compose Desktop's `VerticalScrollbar`).
 * Android: no-op — Android's overscroll glow handles the affordance, and a
 *          drawn scrollbar would look out of place on touch UIs.
 *
 * Caller is responsible for `Modifier.align(...)` since the parent Box layout
 * differs per UI; this helper is just the rendering primitive.
 */
@Composable
expect fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
)
