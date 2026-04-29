package dev.hackathon.linkopener.ui.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    // Touch UIs don't show hover tooltips. The wrapped icons/buttons all
    // have visible icons; the tooltip text is a redundant affordance on
    // desktop. Drop it on Android.
    content()
}
