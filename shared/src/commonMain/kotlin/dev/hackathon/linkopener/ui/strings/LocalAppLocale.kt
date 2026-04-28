package dev.hackathon.linkopener.ui.strings

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
 */
val LocalAppLocale = compositionLocalOf { "" }
