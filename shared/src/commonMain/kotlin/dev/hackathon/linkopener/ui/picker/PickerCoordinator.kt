package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PickerCoordinator(
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    private val getSettings: GetSettingsFlowUseCase,
    private val launcher: LinkLauncher,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<PickerState>(PickerState.Hidden)
    val state: StateFlow<PickerState> = _state.asStateFlow()

    fun handleIncomingUrl(url: String) {
        scope.launch {
            try {
                val all = discoverBrowsers()
                val excluded = getSettings().value.excludedBrowserIds
                val available = all.filterNot { BrowserId(it.bundleId) in excluded }
                _state.value = PickerState.Showing(url = url, browsers = available)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                // If discovery blew up, show an empty picker rather than swallowing
                // the URL silently — the empty UI directs the user to Settings.
                _state.value = PickerState.Showing(url = url, browsers = emptyList())
            }
        }
    }

    fun pickBrowser(browser: Browser) {
        val current = _state.value as? PickerState.Showing ?: return
        val url = current.url
        _state.value = PickerState.Hidden
        scope.launch {
            launcher.openIn(browser, url)
        }
    }

    fun dismiss() {
        _state.value = PickerState.Hidden
    }
}
