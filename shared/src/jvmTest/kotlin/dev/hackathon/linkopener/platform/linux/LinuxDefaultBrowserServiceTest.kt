package dev.hackathon.linkopener.platform.linux

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinuxDefaultBrowserServiceTest {

    private val service = LinuxDefaultBrowserService()

    @Test
    fun cannotOpenSystemSettings() {
        // No consistent settings UI to deep-link into across DEs.
        assertFalse(service.canOpenSystemSettings)
    }

    @Test
    fun isNotDefaultBrowser() = runTest {
        assertFalse(service.isDefaultBrowser())
    }

    @Test
    fun openSystemSettingsReturnsFalseWithoutSideEffects() = runTest {
        assertEquals(false, service.openSystemSettings())
    }

    @Test
    fun observeIsDefaultBrowserEmitsCurrentValueOnceAndCompletes() = runTest {
        // Linux service doesn't override the interface default, so this exercises
        // the DefaultBrowserService.observeIsDefaultBrowser default impl: emit
        // the current isDefaultBrowser() once and complete.
        val emissions = service.observeIsDefaultBrowser().toList()
        assertEquals(listOf(false), emissions)
    }
}
