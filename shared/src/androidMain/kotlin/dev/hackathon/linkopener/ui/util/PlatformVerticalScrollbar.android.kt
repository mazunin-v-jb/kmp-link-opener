package dev.hackathon.linkopener.ui.util

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    // Intentional no-op — Android handles overscroll natively; a drawn
    // scrollbar reads as foreign on touch surfaces.
}
