package dev.hackathon.linkopener.ui.settings

import dev.hackathon.linkopener.core.model.Browser

sealed interface BrowsersState {
    data object Loading : BrowsersState
    data class Loaded(val browsers: List<Browser>) : BrowsersState
    data class Error(val message: String) : BrowsersState
}
