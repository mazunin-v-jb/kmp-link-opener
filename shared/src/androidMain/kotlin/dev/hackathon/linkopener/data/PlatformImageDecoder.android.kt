package dev.hackathon.linkopener.data

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.hackathon.linkopener.coroutines.runCatchingNonCancellation

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatchingNonCancellation {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
