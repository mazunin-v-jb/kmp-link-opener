package dev.hackathon.linkopener.data

import androidx.compose.ui.graphics.ImageBitmap
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation
import dev.hackathon.linkopener.platform.BrowserIconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache that loads browser icons through a [BrowserIconLoader] and
 * exposes them as a single [StateFlow] keyed by `applicationPath`. UI
 * subscribers re-render row-by-row as icons trickle in — discovery isn't
 * blocked on icon extraction.
 *
 * Decoding happens once: the loader returns PNG/SVG bytes, we run them
 * through Skia and store the resulting [ImageBitmap] so each composition
 * reuses the same decoded buffer instead of paying the decode cost per
 * frame.
 *
 * Cache lifetime is the process — browser icons effectively never change
 * between launches. A miss / failure returns `null` for that key (callers
 * fall back to the letter-square avatar) and we don't retry; the next
 * `prefetch` call for the same path is a no-op.
 */
class BrowserIconRepository(
    private val loader: BrowserIconLoader,
    private val logError: (String, Throwable) -> Unit = { tag, t ->
        System.err.println("[$tag] ${t.message}")
    },
) {
    private val _icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val icons: StateFlow<Map<String, ImageBitmap>> = _icons.asStateFlow()

    // Tracks which paths have already been attempted (loaded, in-flight, OR
    // resolved as null). Once a key lands here we never re-fetch — keeps
    // unlikely-to-succeed retries (missing file, unsupported format) from
    // hammering the disk on every settings refresh.
    private val attempted = mutableSetOf<String>()
    private val mutex = Mutex()

    /**
     * Kicks off icon loading for any [applicationPaths] that haven't been
     * tried yet. Returns immediately; results land on [icons] as they
     * resolve. Idempotent — calling repeatedly with the same paths is free.
     */
    fun prefetch(scope: CoroutineScope, applicationPaths: Iterable<String>) {
        scope.launch {
            for (path in applicationPaths.distinct()) {
                val claimed = mutex.withLock { attempted.add(path) }
                if (!claimed) continue
                launch {
                    val result = runCatchingNonCancellation { loader.load(path) }
                        .onFailure { logError("browser-icon", it) }
                        .getOrNull()
                    if (result != null) {
                        val bitmap = decodeImageBitmap(result)
                        if (bitmap != null) {
                            _icons.update { it + (path to bitmap) }
                        }
                    }
                }
            }
        }
    }
}
