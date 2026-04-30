package dev.hackathon.linkopener.ui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Locale-tag nonce that string-using composables read to force themselves out
 * of Compose's smart-skipping when the user-selected language changes.
 *
 * Compose Resources resolves strings against the JVM `Locale.getDefault()`
 * (set in TrayHost). But that mutation alone doesn't trigger recomposition of
 * composables whose other inputs haven't changed — they get skipped, and
 * their stringResource() calls aren't re-evaluated. Reading
 * [LocalAppLocale].current at the top of such a composable adds the nonce to
 * its dependency set; when SettingsScreen flips the provided value, every
 * reader is invalidated and re-runs with the new locale active.
 *
 * Most callers should reach for [useLocaleNonce] instead — it has the same
 * effect with a self-explaining name.
 */
val LocalAppLocale = compositionLocalOf { "" }

/**
 * Subscribes the *calling* composable to [LocalAppLocale] so it recomposes
 * whenever the user switches language. Equivalent to writing
 * `LocalAppLocale.current` at the top of the composable, but the named
 * helper makes the discipline self-documenting (and harder to delete by
 * accident, since "this line looks unused" is a frequent first impression).
 *
 * **Implementation note**: marked `@NonRestartableComposable` on purpose.
 * A plain `@Composable fun useLocaleNonce()` creates its own restart scope,
 * so when [LocalAppLocale] changes only `useLocaleNonce`'s scope is
 * invalidated — and since its body just reads-and-discards the value, the
 * caller never recomposes and stays smart-skipped on the previous locale.
 * `@NonRestartableComposable` inlines the read into the caller's restart
 * scope, which is the actual subscription we want.
 *
 * Add a call to this helper at the top of any composable that calls
 * `stringResource(...)` but takes no settings-derived parameter — without
 * it the composable would be smart-skipped on a language change.
 */
@Composable
@NonRestartableComposable
fun useLocaleNonce() {
    LocalAppLocale.current
}
