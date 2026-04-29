package dev.hackathon.linkopener.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.hackathon.linkopener.ui.picker.BrowserPickerScreen
import dev.hackathon.linkopener.ui.picker.PickerState
import dev.hackathon.linkopener.ui.theme.LinkOpenerTheme

/**
 * Receives `ACTION_VIEW http(s)` intents and shows the browser picker over a
 * dimmed translucent background. Selecting a browser launches the URL there
 * via Intent.setPackage and finishes us; tapping outside the card dismisses.
 *
 * `singleInstance` in the manifest collapses rapid-fire link clicks into a
 * single activity instance — `onNewIntent` re-feeds the new URL into the
 * coordinator without spawning another picker on top of the current one.
 *
 * The container's `urlReceiver` is a thin pipe to the picker coordinator
 * (`AndroidUrlReceiver.submit(url)`); `start { ... }` is wired in the
 * container's init, so we just `submit`.
 */
class PickerActivity : ComponentActivity() {

    private val container by lazy { (application as LinkOpenerApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forwardIntent(intent)

        setContent {
            val state by container.pickerCoordinator.state.collectAsState()
            val icons by container.browserIconRepository.icons.collectAsState()
            val settings by container.getSettingsFlowUseCase().collectAsState()

            // Coordinator flips state to Hidden after pickBrowser() launches
            // the target intent, OR if a Direct rule decision didn't surface
            // a picker at all. Either way our job is done — finish().
            LaunchedEffect(state) {
                if (state is PickerState.Hidden) finish()
            }

            LinkOpenerTheme(theme = settings.theme) {
                val current = state
                if (current is PickerState.Showing) {
                    val noRipple = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = noRipple,
                                indication = null,
                                onClick = { container.pickerCoordinator.dismiss() },
                            )
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Stop bubble-through clicks so taps inside the card
                        // don't dismiss it.
                        Surface(
                            modifier = Modifier
                                .padding(16.dp)
                                .clickable(
                                    interactionSource = noRipple,
                                    indication = null,
                                    onClick = {},
                                ),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                        ) {
                            BrowserPickerScreen(
                                url = current.url,
                                browsers = current.browsers,
                                onPick = { browser ->
                                    container.pickerCoordinator.pickBrowser(browser)
                                },
                                icons = icons,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-feeds replacement intents into the picker (singleInstance mode
     * delivers them here rather than restarting the activity).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardIntent(intent)
    }

    private fun forwardIntent(intent: Intent?) {
        val url = extractUrl(intent)
        if (url == null) {
            // Defensive: if PickerActivity gets started without a usable
            // URL we just bail. Better than leaving a transparent shell
            // activity hanging on top of whatever the user was doing.
            finish()
            return
        }
        container.urlReceiver.submit(url)
    }

    /**
     * Pulls a URL out of either an `ACTION_VIEW http(s)://…` intent (the
     * standard browser entry) or an `ACTION_SEND text/plain` intent
     * (Android share sheets). Returns null if neither shape carries a
     * usable URL.
     */
    private fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        // ACTION_VIEW carries the URL as the intent data. The manifest
        // intent-filter restricts to http/https schemes, so dataString is
        // either a full URL or null.
        intent.dataString?.let { return it }
        // ACTION_SEND: EXTRA_TEXT can be a bare URL or arbitrary text
        // containing one. Pick the first http/https token if any — that's
        // typically how share sheets format "Share link" payloads.
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return URL_PATTERN.find(text)?.value
        }
        return null
    }

    private companion object {
        // Loose URL match: scheme + host with at least one dot, optional
        // path/query. Greedy stops at whitespace. Good enough for typical
        // share-sheet payloads ("Look at this: https://example.com/foo");
        // not RFC 3986.
        private val URL_PATTERN = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    }
}
