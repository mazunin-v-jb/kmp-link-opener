package dev.hackathon.linkopener.platform.android

import dev.hackathon.linkopener.platform.UrlReceiver
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the Activity-driven Intent system to the desktop-shaped
 * `UrlReceiver` interface. The desktop impl reads URLs off stdin in a
 * background thread; on Android URLs arrive via `Intent.dataString` in
 * `Activity.onCreate` / `onNewIntent`, which the Activity must forward
 * by calling [submit].
 *
 * Holds the callback in an `AtomicReference` so the Activity can submit
 * URLs from any thread (intents arrive on the main thread, but tests
 * run on background threads).
 *
 * The first URL submitted before [start] is buffered (`pendingUrl`) and
 * delivered as soon as the consumer subscribes. This handles the
 * cold-start case where the Activity creates the receiver, immediately
 * forwards the launch intent, and only then calls `start { ... }` from
 * the AppContainer.
 */
class AndroidUrlReceiver : UrlReceiver {
    private val callback = AtomicReference<((String) -> Unit)?>(null)
    private val pendingUrl = AtomicReference<String?>(null)

    override fun start(onUrl: (String) -> Unit) {
        callback.set(onUrl)
        pendingUrl.getAndSet(null)?.let(onUrl)
    }

    /**
     * Forward an intercepted URL into the picker chain. Activity calls
     * this from `onCreate(intent)` and `onNewIntent(intent)`.
     */
    fun submit(url: String) {
        val cb = callback.get()
        if (cb != null) cb(url) else pendingUrl.set(url)
    }
}
