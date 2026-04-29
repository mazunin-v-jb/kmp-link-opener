package dev.hackathon.linkopener.platform.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests cover the two paths the loader takes:
 *
 * 1. **Public load() failure**: `getApplicationIcon` for a package that
 *    isn't installed throws (or, on Robolectric, returns null for
 *    framework-resource icons we can't resolve in the test classpath).
 *    Either way `runCatching` swallows it and returns null — the caller
 *    falls back to a letter avatar.
 *
 * 2. **Raster helper directly**: `Drawable.toPngBytes` is `internal` so
 *    we can hand it a constructed BitmapDrawable / ColorDrawable and
 *    verify the encode roundtrips. Avoids depending on Robolectric's
 *    PackageManager-icon shadow which doesn't ship the framework
 *    drawables we'd need.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidBrowserIconLoaderTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun loadReturnsNullForUninstalledPackage() = runTest {
        // PackageManager.getApplicationIcon throws NameNotFoundException
        // on unknown packages. The loader's runCatching folds it to null
        // so the icon repository can fall back to the letter avatar.
        val loader = AndroidBrowserIconLoader(application)
        val bytes = loader.load("com.nonexistent.fake.package")
        assertNull(bytes)
    }

    @Test
    fun toPngBytesEncodesBitmapDrawable() {
        // Hand-rolled 64×64 red BitmapDrawable. `toPngBytes(128)` should
        // upscale to 128×128 (since intrinsic 64 < 128) and re-encode.
        val src = makeBitmap(64, 64, Color.RED)
        val drawable = BitmapDrawable(application.resources, src)

        val bytes = drawable.toPngBytes(128)

        assertTrue(bytes.isNotEmpty(), "Encoded bytes must not be empty")
        assertPngSignature(bytes)
    }

    @Test
    fun toPngBytesReusesBitmapWhenSourceIsLargeEnough() {
        // Source 256×256, request 128 → loader should NOT downscale
        // (we want to keep the highest-fidelity raster up to the
        // launcher's display density).
        val src = makeBitmap(256, 256, Color.GREEN)
        val drawable = BitmapDrawable(application.resources, src)

        val bytes = drawable.toPngBytes(128)

        assertPngSignature(bytes)
        // Decode the result and check it kept the source size — this
        // is the contract of the "if intrinsic >= size, reuse" branch.
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(256, decoded.width)
        assertEquals(256, decoded.height)
    }

    @Test
    fun toPngBytesRasterisesNonBitmapDrawableAtTargetSize() {
        // ColorDrawable has no intrinsic bitmap — the loader has to
        // create a fresh Bitmap at the target size and draw the
        // drawable into it.
        val drawable = ColorDrawable(Color.BLUE)

        val bytes = drawable.toPngBytes(96)

        assertPngSignature(bytes)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(96, decoded.width)
        assertEquals(96, decoded.height)
    }

    private fun makeBitmap(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return bmp
    }

    private fun assertPngSignature(bytes: ByteArray) {
        // PNG signature: 0x89 0x50 0x4E 0x47 (\x89 P N G).
        assertTrue(bytes.size >= 4)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }
}
