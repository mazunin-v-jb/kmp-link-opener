package dev.hackathon.linkopener.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation
import org.jetbrains.skia.Image as SkiaImage

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatchingNonCancellation {
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()
