package dev.hackathon.linkopener.platform.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import dev.hackathon.linkopener.platform.BrowserIconLoader
import java.io.ByteArrayOutputStream

/**
 * Returns a browser app's launcher icon as PNG bytes. The
 * `applicationPath` field on Android carries the package id (set by
 * [AndroidBrowserDiscovery]); we hand it to
 * `PackageManager.getApplicationIcon(packageName)` and rasterise the
 * resulting `Drawable` to a PNG so `BrowserIconRepository`'s decode path
 * stays the same as desktop (encoded bytes → ImageBitmap).
 *
 * Adaptive icons are rasterised at the target size and pick up their
 * default mask shape — good enough for the picker and Settings rows.
 */
class AndroidBrowserIconLoader(
    private val context: Context,
    private val sizePx: Int = 128,
) : BrowserIconLoader {

    override suspend fun load(applicationPath: String): ByteArray? = runCatching {
        val drawable: Drawable = context.packageManager.getApplicationIcon(applicationPath)
        drawable.toPngBytes(sizePx)
    }.getOrNull()
}

private fun Drawable.toPngBytes(size: Int): ByteArray {
    val bitmap: Bitmap = if (this is BitmapDrawable && this.bitmap != null) {
        // Already-rasterised drawable — reuse its bitmap, scaling only if
        // the source is larger than we asked for (no point upscaling).
        if (intrinsicWidth >= size && intrinsicHeight >= size) {
            this.bitmap
        } else {
            Bitmap.createScaledBitmap(this.bitmap, size, size, true)
        }
    } else {
        // Adaptive / vector drawable — rasterise at target size.
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }
    val out = ByteArrayOutputStream(8 * 1024)
    bitmap.compress(Bitmap.CompressFormat.PNG, /* quality */ 100, out)
    return out.toByteArray()
}
