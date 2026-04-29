package dev.hackathon.linkopener.data

import dev.hackathon.linkopener.platform.BrowserIconLoader
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class BrowserIconRepositoryTest {

    // 1×1 transparent PNG, built once via ImageIO so the bytes carry valid
    // CRCs. Hand-rolled hex would also work but keeping CRC32 correct by
    // hand is finicky; letting the JDK encode it keeps the test honest about
    // what it's exercising (Skia decode of a real PNG).
    private val onePixelPng: ByteArray by lazy {
        val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        check(ImageIO.write(img, "png", out)) { "ImageIO failed to encode PNG" }
        out.toByteArray()
    }

    private class CountingLoader(
        private val responses: Map<String, ByteArray?>,
    ) : BrowserIconLoader {
        val callCount = AtomicInteger(0)
        val seenPaths = mutableListOf<String>()
        override suspend fun load(applicationPath: String): ByteArray? {
            callCount.incrementAndGet()
            synchronized(seenPaths) { seenPaths.add(applicationPath) }
            return responses[applicationPath]
        }
    }

    @Test
    fun `prefetch loads icons and emits them to the flow`() = runTest {
        // Skia decode smoke test — if this throws, skiko's native lib isn't
        // on the test classpath and the bigger test below won't tell us why.
        val skiaImg = org.jetbrains.skia.Image.makeFromEncoded(onePixelPng)
        println("[diagnostic] skia image w=${skiaImg.width} h=${skiaImg.height}")

        val loader = CountingLoader(mapOf("/Apps/Foo.app" to onePixelPng))
        val repo = BrowserIconRepository(loader, logError = { tag, t ->
            println("[diagnostic] error tag=$tag msg=${t.message} class=${t.javaClass.simpleName}")
        })

        repo.prefetch(this, listOf("/Apps/Foo.app"))
        advanceUntilIdle()

        assertEquals(1, loader.callCount.get())
        val bitmap = repo.icons.value["/Apps/Foo.app"]
        assertNotNull(bitmap, "icon should be decoded and stored after prefetch")
        // 1×1 PNG → 1×1 ImageBitmap. Asserting the dimensions guards against
        // the decoder silently returning the wrong-sized output (e.g. cached
        // bitmap mistakenly reused).
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }

    @Test
    fun `prefetch deduplicates already-attempted paths within one call`() = runTest {
        val loader = CountingLoader(mapOf("/Apps/Foo.app" to onePixelPng))
        val repo = BrowserIconRepository(loader, logError = { _, _ -> })

        // Same path repeated five times in one call — must hit the loader
        // only once.
        repo.prefetch(this, List(5) { "/Apps/Foo.app" })
        advanceUntilIdle()

        assertEquals(1, loader.callCount.get())
    }

    @Test
    fun `prefetch deduplicates across separate calls (cache lifetime is process)`() = runTest {
        val loader = CountingLoader(mapOf("/Apps/Foo.app" to onePixelPng))
        val repo = BrowserIconRepository(loader, logError = { _, _ -> })

        repo.prefetch(this, listOf("/Apps/Foo.app"))
        advanceUntilIdle()
        repo.prefetch(this, listOf("/Apps/Foo.app"))
        advanceUntilIdle()
        repo.prefetch(this, listOf("/Apps/Foo.app"))
        advanceUntilIdle()

        // Repeated prefetches for paths already in the icon map (or already
        // attempted) must be no-ops — browser icons effectively never change
        // between launches, so refetching is wasted work.
        assertEquals(1, loader.callCount.get())
    }

    @Test
    fun `loader returning null leaves the path unmapped but marks it attempted`() = runTest {
        // Two-step: first prefetch returns null (e.g. file doesn't exist on
        // disk), the second prefetch must NOT retry — that prevents endless
        // disk hammering for browsers we'll never resolve (e.g. uninstalled
        // tools the user never removed from their list).
        val loader = CountingLoader(mapOf("/Apps/Ghost.app" to null))
        val repo = BrowserIconRepository(loader, logError = { _, _ -> })

        repo.prefetch(this, listOf("/Apps/Ghost.app"))
        advanceUntilIdle()
        assertEquals(1, loader.callCount.get())
        assertNull(repo.icons.value["/Apps/Ghost.app"])

        repo.prefetch(this, listOf("/Apps/Ghost.app"))
        advanceUntilIdle()
        assertEquals(1, loader.callCount.get(), "second prefetch should be a no-op")
    }

    @Test
    fun `loader exception is captured by logError and other paths still load`() = runTest {
        // One bad apple shouldn't kill the prefetch batch — Settings would
        // otherwise show letter avatars for every browser if any single path
        // happened to throw during extraction.
        val errors = mutableListOf<Pair<String, Throwable>>()
        val loader = object : BrowserIconLoader {
            override suspend fun load(applicationPath: String): ByteArray? {
                if (applicationPath.contains("Bad")) error("disk on fire")
                return onePixelPng
            }
        }
        val repo = BrowserIconRepository(loader) { tag, t -> errors.add(tag to t) }

        repo.prefetch(this, listOf("/Apps/Bad.app", "/Apps/Good.app"))
        advanceUntilIdle()

        assertEquals(1, errors.size, "the failing path should produce exactly one log entry")
        assertEquals("browser-icon", errors.first().first)
        assertNotNull(repo.icons.value["/Apps/Good.app"], "the surviving path should still resolve")
        assertNull(repo.icons.value["/Apps/Bad.app"])
    }

    @Test
    fun `multiple paths in one call all dispatch in parallel`() = runTest(StandardTestDispatcher()) {
        val loader = CountingLoader(
            mapOf(
                "/Apps/A.app" to onePixelPng,
                "/Apps/B.app" to onePixelPng,
                "/Apps/C.app" to onePixelPng,
            ),
        )
        val repo = BrowserIconRepository(loader, logError = { _, _ -> })

        repo.prefetch(this, listOf("/Apps/A.app", "/Apps/B.app", "/Apps/C.app"))
        advanceUntilIdle()

        assertEquals(3, loader.callCount.get())
        assertTrue(repo.icons.value.keys.containsAll(setOf("/Apps/A.app", "/Apps/B.app", "/Apps/C.app")))
    }
}
