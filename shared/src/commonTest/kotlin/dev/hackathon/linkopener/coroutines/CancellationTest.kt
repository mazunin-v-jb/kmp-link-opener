package dev.hackathon.linkopener.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class RunCatchingNonCancellationTest {

    @Test
    fun successfulBlockReturnsResultSuccess() {
        val result = runCatchingNonCancellation { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrThrow())
    }

    @Test
    fun unitReturnIsCaptured() {
        val recorded = mutableListOf<String>()
        val result = runCatchingNonCancellation { recorded += "ran" }

        assertTrue(result.isSuccess)
        assertEquals(listOf("ran"), recorded)
    }

    @Test
    fun nonCancellationThrowableIsCapturedAsFailure() {
        val boom = IllegalStateException("boom")
        val result = runCatchingNonCancellation<Int> { throw boom }

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun runtimeExceptionIsCapturedAsFailure() {
        val result = runCatchingNonCancellation<String> { throw RuntimeException("oops") }

        assertTrue(result.isFailure)
        val err = result.exceptionOrNull()
        assertIs<RuntimeException>(err)
        assertEquals("oops", err.message)
    }

    @Test
    fun errorIsCapturedAsFailure() {
        // Errors (subtypes of java.lang.Error / kotlin's Error) are still
        // Throwables; helper captures them like any other non-cancellation.
        val result = runCatchingNonCancellation<Int> { throw AssertionError("nope") }

        assertTrue(result.isFailure)
        assertIs<AssertionError>(result.exceptionOrNull())
    }

    @Test
    fun cancellationExceptionIsRethrown() {
        val ex = assertFails {
            runCatchingNonCancellation<Int> { throw CancellationException("cancelled") }
        }

        assertIs<CancellationException>(ex)
        assertEquals("cancelled", ex.message)
    }

    @Test
    fun customCancellationSubclassIsRethrown() {
        // Any subclass of CancellationException must propagate, not be
        // caught — this is the whole point of the helper.
        class CustomCancel(message: String) : CancellationException(message)

        val ex = assertFails {
            runCatchingNonCancellation<Unit> { throw CustomCancel("nested") }
        }

        assertIs<CancellationException>(ex)
        assertEquals("nested", ex.message)
    }

    @Test
    fun structuredCancellationFromOutsidePropagates() = runTest {
        // Real-world scenario: the helper sits inside a coroutine whose
        // parent gets cancelled mid-execution. The CancellationException
        // raised by the suspending operation must surface, not be swallowed
        // into a Result.failure (which would leak the cancelled coroutine).
        var swallowed = false
        coroutineScope {
            val job = launch {
                runCatchingNonCancellation {
                    delay(1_000)
                }.onFailure { swallowed = true }
            }
            // Let the launch reach its delay, then cancel.
            testScheduler.runCurrent()
            job.cancel()
            job.join()
        }

        assertEquals(false, swallowed)
    }

    @Test
    fun chainsLikeRunCatching_onSuccess_isRunOnSuccessfulPath() {
        var observed: Int? = null
        runCatchingNonCancellation { 7 }.onSuccess { observed = it }

        assertEquals(7, observed)
    }

    @Test
    fun chainsLikeRunCatching_onFailure_isRunOnFailurePath() {
        var observed: Throwable? = null
        runCatchingNonCancellation<Int> { throw IllegalStateException("x") }
            .onFailure { observed = it }

        assertIs<IllegalStateException>(observed)
    }

    @Test
    fun getOrDefaultReturnsFallbackOnFailure() {
        val value = runCatchingNonCancellation<Int> { throw RuntimeException() }
            .getOrDefault(99)
        assertEquals(99, value)
    }

    @Test
    fun getOrDefaultReturnsValueOnSuccess() {
        val value = runCatchingNonCancellation { 7 }.getOrDefault(99)
        assertEquals(7, value)
    }

    @Test
    fun helperDoesNotCatchExceptionsThrownDuringResultConsumption() {
        // .onFailure { throw … } should propagate — the helper only
        // protects the *block*, not the chained handler.
        assertFails {
            runCatchingNonCancellation<Int> { throw RuntimeException("a") }
                .onFailure { throw IllegalArgumentException("b") }
        }
    }

    @Test
    fun rethrownCancellationDoesNotInvokeOnFailure() {
        var observed = false
        try {
            runCatchingNonCancellation<Int> { throw CancellationException("c") }
                .onFailure { observed = true }
            fail("should have rethrown")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(false, observed)
    }
}
