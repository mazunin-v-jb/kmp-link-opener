package dev.hackathon.linkopener.coroutines

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but [CancellationException] is rethrown instead of
 * being captured as a failure. Coroutine cancellation must always propagate
 * — swallowing it leaves the calling coroutine alive past the point its
 * parent considers it dead, with the predictable result of subtle bugs and
 * leaked work.
 *
 * Use this anywhere you'd otherwise write the five-line
 * `try { … } catch (CancellationException) { throw it } catch (Throwable) { … }`
 * dance. Then chain `.onFailure { … }` / `.getOrDefault(…)` / `.fold(…)` as
 * usual on the returned [Result].
 *
 * Example:
 * ```
 * runCatchingNonCancellation { discoverBrowsers() }
 *     .onFailure { System.err.println("[discovery] $it") }
 *     .getOrDefault(emptyList())
 * ```
 */
inline fun <R> runCatchingNonCancellation(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (t: CancellationException) {
    throw t
} catch (t: Throwable) {
    Result.failure(t)
}
