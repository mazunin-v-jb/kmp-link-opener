package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.Browser

sealed interface PickerState {
    data object Hidden : PickerState
    data class Showing(
        val url: String,
        val browsers: List<Browser>,
    ) : PickerState
}
