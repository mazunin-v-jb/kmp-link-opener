package dev.hackathon.linkopener.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoOpAutoStartManagerTest {

    @Test
    fun isEnabledAlwaysFalse() = runTest {
        assertFalse(NoOpAutoStartManager().isEnabled())
    }

    @Test
    fun setEnabledIsANoOpAndDoesNotChangeIsEnabled() = runTest {
        val manager = NoOpAutoStartManager()

        manager.setEnabled(true)
        assertFalse(manager.isEnabled())

        manager.setEnabled(false)
        assertFalse(manager.isEnabled())
    }

    @Test
    fun setEnabledReturnsUnit() = runTest {
        val result: Unit = NoOpAutoStartManager().setEnabled(true)
        assertEquals(Unit, result)
    }
}
