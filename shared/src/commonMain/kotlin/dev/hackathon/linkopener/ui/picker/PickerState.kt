package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId

sealed interface PickerState {
    data object Hidden : PickerState
    data class Showing(
        val url: String,
        val browsers: List<Browser>,
        // Subset of [browsers] whose process the host reports as currently
        // running. Three-state semantics matter here:
        //  - `null` — probe wasn't attempted / unsupported (Android,
        //    coroutine-cancelled). Picker treats this as "no info" and
        //    leaves every row fully opaque.
        //  - empty set — probe ran successfully and matched nothing.
        //    Picker fades EVERY row and shows "(not running)" for each.
        //  - non-empty set — exactly the rows whose ids aren't in the
        //    set get faded.
        val runningBrowserIds: Set<BrowserId>? = null,
    ) : PickerState
}
