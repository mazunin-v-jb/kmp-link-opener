package dev.hackathon.linkopener.ui.picker

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation
import dev.hackathon.linkopener.data.BrowserIconRepository
import dev.hackathon.linkopener.domain.RuleDecision
import dev.hackathon.linkopener.domain.RuleEngine
import dev.hackathon.linkopener.domain.applyUserOrder
import dev.hackathon.linkopener.domain.usecase.DiscoverBrowsersUseCase
import dev.hackathon.linkopener.domain.usecase.GetSettingsFlowUseCase
import dev.hackathon.linkopener.platform.LinkLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PickerCoordinator(
    private val discoverBrowsers: DiscoverBrowsersUseCase,
    private val getSettings: GetSettingsFlowUseCase,
    private val launcher: LinkLauncher,
    private val ruleEngine: RuleEngine,
    // Optional so tests that don't care about icons can pass `null`. In
    // production the icon repo is always wired through AppContainer.
    private val iconRepository: BrowserIconRepository? = null,
    private val scope: CoroutineScope,
    // Failures here are rare but always interesting — the picker is the app's
    // hottest path. Default to stderr so they show up in Console.app even
    // outside debug mode (mirrors what AppContainer does for startup
    // discovery). Tests pass a no-op or a recorder.
    private val logError: (String, Throwable) -> Unit = { tag, t ->
        System.err.println("[$tag] ${t.message}")
    },
) {
    private val _state = MutableStateFlow<PickerState>(PickerState.Hidden)
    val state: StateFlow<PickerState> = _state.asStateFlow()

    fun handleIncomingUrl(url: String) {
        scope.launch {
            runCatchingNonCancellation {
                val all = discoverBrowsers()
                val settings = getSettings().value
                // Rule engine sees the full discovered list and the
                // exclusions set; it owns decisions #4 (exclusion wins) and
                // #5 (skip if browser missing), so this call site doesn't
                // pre-filter — it just respects the engine's verdict.
                val decision = ruleEngine.resolve(
                    url = url,
                    rules = settings.rules,
                    browsers = all,
                    exclusions = settings.excludedBrowserIds,
                )
                if (decision is RuleDecision.Direct) {
                    launcher.openIn(decision.browser, url)
                    return@runCatchingNonCancellation
                }
                val available = all.filterNot { it.toBrowserId() in settings.excludedBrowserIds }
                val ordered = applyUserOrder(available, settings.browserOrder)
                // No-choice short-circuit: if exclusions/discovery left exactly
                // one browser, the picker would just be a single-row "tap me"
                // dialog — skip it and launch directly. Empty list still falls
                // through to the empty Showing state so the user sees an
                // explanation instead of nothing.
                if (ordered.size == 1) {
                    launcher.openIn(ordered.single(), url)
                    return@runCatchingNonCancellation
                }
                // Idempotent — repo skips paths that have already been
                // attempted, so the cost is a no-op once warmed up by
                // AppContainer's startup discovery dump.
                iconRepository?.prefetch(scope, ordered.map { it.applicationPath })
                _state.value = PickerState.Showing(url = url, browsers = ordered)
            }.onFailure {
                // If discovery blew up, show an empty picker rather than swallowing
                // the URL silently — the empty UI directs the user to Settings.
                // Also log so a dev sees why; without this the failure is
                // invisible outside breakpoints.
                logError("picker", it)
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
