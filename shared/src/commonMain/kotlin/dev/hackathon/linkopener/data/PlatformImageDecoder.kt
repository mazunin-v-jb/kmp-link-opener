package dev.hackathon.linkopener.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodes encoded image bytes (PNG / JPEG / etc.) into a Compose [ImageBitmap].
 *
 * Returns null if the bytes don't decode as a known format. Implementations
 * MUST swallow decoder exceptions and return null — this is called from
 * `BrowserIconRepository`, which is fire-and-forget and can fall back to the
 * letter-square avatar.
 *
 * Per-platform actuals:
 * - JVM (desktop): Skia (`org.jetbrains.skia.Image.makeFromEncoded`).
 * - Android: `BitmapFactory.decodeByteArray` + `asImageBitmap`.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
