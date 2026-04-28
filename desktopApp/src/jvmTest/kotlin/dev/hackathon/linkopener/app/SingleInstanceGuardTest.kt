package dev.hackathon.linkopener.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceGuardTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("single-instance-guard-test")
    }

    @AfterTest
    fun tearDown() {
        // Best-effort cleanup. The lock file may still be open if a test threw
        // before releasing — leave it; the JVM exit will close descriptors and
        // the OS will GC the temp dir.
        runCatching {
            Files.walk(tempDir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
            }
        }
    }

    @Test
    fun firstAcquireReturnsPrimaryGuard() {
        val guard = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(guard, "first acquire on empty dir should yield a primary guard")
        guard.release()
    }

    @Test
    fun secondAcquireReturnsNullAndPingsPrimary() {
        val activated = CountDownLatch(1)
        val primary = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(primary)
        primary.onActivationRequest = { activated.countDown() }
        try {
            val secondary = SingleInstanceGuard.acquireOrSignal(tempDir)
            assertNull(secondary, "second acquire while first is held must fail")
            assertTrue(
                activated.await(2, TimeUnit.SECONDS),
                "primary should receive activation ping from the secondary within 2s",
            )
        } finally {
            primary.release()
        }
    }

    @Test
    fun releaseAllowsNextCallerToBecomePrimary() {
        val first = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(first)
        first.release()

        val second = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(second, "after the first guard releases, the next acquire should succeed")
        second.release()
    }

    @Test
    fun staleOrCorruptPortFileDoesNotBreakSecondaryShutdown() {
        // Simulate a previously-crashed primary that left a junk port file.
        // No primary is holding the lock, so the *next* caller becomes primary
        // and must overwrite the stale port file rather than choke on it.
        tempDir.resolve("instance.port").writeText("not-a-port")

        val guard = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(guard, "stale port file should not prevent acquiring the lock")
        guard.release()
    }

    @Test
    fun secondaryWithStalePortFileBailsCleanly() {
        // Hold the lock so the *next* caller goes down the secondary branch,
        // and ensure that even if the port file is corrupt, the secondary
        // path doesn't throw — it just returns null.
        val primary = SingleInstanceGuard.acquireOrSignal(tempDir)
        assertNotNull(primary)
        try {
            // Overwrite the legitimate port file the primary just wrote.
            tempDir.resolve("instance.port").writeText("not-a-port")
            val secondary = SingleInstanceGuard.acquireOrSignal(tempDir)
            assertNull(secondary)
        } finally {
            primary.release()
        }
    }

    @Test
    fun acquireReleaseLoopDoesNotLeakThreads() {
        val baseline = Thread.activeCount()
        repeat(10) {
            val guard = SingleInstanceGuard.acquireOrSignal(tempDir)
            assertNotNull(guard)
            guard.release()
        }
        // Allow daemon listener threads time to actually exit after release.
        Thread.sleep(200)
        val drift = Thread.activeCount() - baseline
        assertTrue(drift < 5, "thread count drifted by $drift after 10 acquire/release cycles")
        // Sanity: tearDown is allowed to clean up the lock file too.
        tempDir.resolve("instance.lock").deleteIfExists()
    }
}
