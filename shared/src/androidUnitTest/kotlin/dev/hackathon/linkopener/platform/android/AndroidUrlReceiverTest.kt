package dev.hackathon.linkopener.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * AndroidUrlReceiver bridges the activity-driven Intent system to the
 * desktop-shaped [start { onUrl }] callback. These tests cover the
 * delivery pipeline + the cold-start buffering case (URL submitted
 * before start() — happens when PickerActivity forwards the launch
 * intent before the AppContainer has finished wiring).
 */
class AndroidUrlReceiverTest {

    @Test
    fun deliversSubmittedUrlsToActiveCallback() {
        val received = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()
        receiver.start { received += it }

        receiver.submit("http://example.com")
        receiver.submit("https://github.com")

        assertEquals(listOf("http://example.com", "https://github.com"), received)
    }

    @Test
    fun buffersOneUrlSubmittedBeforeStart() {
        // Cold-start case: PickerActivity reads intent.dataString in
        // onCreate and calls submit BEFORE AndroidAppContainer.init has
        // run start{}. Receiver must hold onto the URL until start()
        // shows up, then flush it.
        val received = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()

        receiver.submit("http://example.com")

        // Flush on start.
        receiver.start { received += it }

        assertEquals(listOf("http://example.com"), received)
    }

    @Test
    fun keepsOnlyMostRecentUrlWhenMultipleSubmittedBeforeStart() {
        // Buffer is single-slot — if a second URL arrives before start
        // (e.g. user clicks two links rapid-fire while the picker boots)
        // the later one wins. We never want to deliver stale URLs out
        // of order. Tests the contract; if the policy ever changes
        // (e.g. queue all), this test should be updated.
        val received = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()

        receiver.submit("http://first.example")
        receiver.submit("http://second.example")
        receiver.start { received += it }

        assertEquals(listOf("http://second.example"), received)
    }

    @Test
    fun deliversDirectlyOnceCallbackIsActiveDoesNotBufferAgain() {
        // After the buffered URL flushes via start(), the receiver
        // should NOT keep buffering — subsequent submits go straight
        // to the live callback without stashing.
        val received = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()

        receiver.submit("http://buffered.example")
        receiver.start { received += it }
        receiver.submit("http://direct.example")

        assertEquals(listOf("http://buffered.example", "http://direct.example"), received)
    }

    @Test
    fun startReplacesActiveCallback() {
        // start() may be called multiple times during lifecycle (e.g. an
        // Activity reconfiguration could in theory rewire). Each call
        // should fully replace the previous callback — no broadcasting
        // to old callbacks, no leaking listeners.
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()

        receiver.start { first += it }
        receiver.start { second += it }
        receiver.submit("http://x.example")

        assertEquals(emptyList(), first)
        assertEquals(listOf("http://x.example"), second)
    }

    @Test
    fun startWithNoBufferedUrlEmitsNothing() {
        // No URL submitted before start() — the start callback should
        // not be invoked spuriously with null or the empty string.
        var receivedAny = false
        val receiver = AndroidUrlReceiver()

        receiver.start { receivedAny = true }

        assertEquals(false, receivedAny)
    }

    @Test
    fun pendingUrlIsClearedAfterFirstFlush() {
        // After flushing, calling start() AGAIN with a fresh listener
        // should not re-deliver the previously buffered URL — that'd
        // be a phantom replay.
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val receiver = AndroidUrlReceiver()

        receiver.submit("http://buffered.example")
        receiver.start { first += it }
        receiver.start { second += it }

        assertEquals(listOf("http://buffered.example"), first)
        assertEquals(emptyList(), second)
    }

    @Test
    fun submitsFromMultipleThreadsAreAllDeliveredEventually() {
        // The internal AtomicReference is meant to make submit() safe
        // from any thread (intents arrive on main; tests / coroutines
        // on background threads). This test fires N submits from a
        // small thread pool and checks every one lands.
        val received = java.util.Collections.synchronizedList(mutableListOf<String>())
        val receiver = AndroidUrlReceiver()
        receiver.start { received += it }

        val threads = (0 until 8).map { i ->
            Thread {
                repeat(50) { j -> receiver.submit("http://t$i-$j.example") }
            }.apply { start() }
        }
        threads.forEach { it.join() }

        // 8 threads × 50 submits — all should arrive (order doesn't matter).
        assertEquals(400, received.size)
    }

    @Test
    fun bufferedSlotDefaultsToNullBeforeAnySubmit() {
        // Implementation-leak test: empty AtomicReference state defaults
        // to null. Captured via reflection-free means: a start() with
        // no prior submit must not invoke the callback. Already covered
        // above, but this asserts the precise return value of getAndSet.
        val receiver = AndroidUrlReceiver()
        val pending: String? = run {
            // We can observe the buffer state by submitting nothing then
            // checking that start() flushes nothing.
            var captured: String? = null
            receiver.start { captured = it }
            captured
        }
        assertNull(pending)
    }
}
