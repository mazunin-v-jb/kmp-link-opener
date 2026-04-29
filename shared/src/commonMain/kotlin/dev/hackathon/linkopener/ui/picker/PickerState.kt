package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId

sealed interface PickerState {
    data object Hidden : PickerState
    data class Showing(
        val url: String,
        val browsers: List<Browser>,
        // Subset of [browsers] whose process the host reports as currently
        // running. Empty on Android (no API to query other apps' state) and
        // when the probe times out — picker treats empty as "no info" and
        // renders all rows fully opaque.
        val runningBrowserIds: Set<BrowserId> = emptySet(),
    ) : PickerState
}
