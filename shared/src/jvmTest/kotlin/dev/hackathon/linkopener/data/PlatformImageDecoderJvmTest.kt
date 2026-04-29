package dev.hackathon.linkopener.data

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for the JVM `actual` of [decodeImageBitmap]. The actual
 * runs Skia (`org.jetbrains.skia.Image.makeFromEncoded`) under the
 * hood, which is portable across desktop OSes — no platform gating
 * needed in the test.
 *
 * Test fixtures are generated at test time via `javax.imageio.ImageIO`
 * (always present on JVM) so we don't ship pre-rolled binary blobs in
 * the repo.
 */
class PlatformImageDecoderJvmTest {

    @Test
    fun decodesValidPngBytes() {
        val bitmap = decodeImageBitmap(generatePng(1, 1))

        assertNotNull(bitmap, "Expected Skia to decode a valid 1x1 PNG")
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }

    @Test
    fun decodesNonSquarePng() {
        val bitmap = decodeImageBitmap(generatePng(64, 32))

        assertNotNull(bitmap)
        assertEquals(64, bitmap.width)
        assertEquals(32, bitmap.height)
    }

    @Test
    fun returnsNullForGarbageBytes() {
        val bitmap = decodeImageBitmap(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertNull(bitmap, "Garbage bytes should not decode")
    }

    @Test
    fun returnsNullForEmptyByteArray() {
        val bitmap = decodeImageBitmap(byteArrayOf())
        assertNull(bitmap)
    }

    @Test
    fun decodesSameBytesIndependentlyOnEachCall() {
        // Two decode calls on the same bytes should produce equivalent
        // bitmaps — there's no caching layer, just stateless decode.
        val bytes = generatePng(16, 16)
        val first = decodeImageBitmap(bytes)
        val second = decodeImageBitmap(bytes)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
    }

    private fun generatePng(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return out.toByteArray()
    }
}
