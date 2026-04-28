package dev.hackathon.linkopener.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyBrowserDiscoveryTest {

    @Test
    fun returnsEmptyListForAnyOsName() = runTest {
        assertEquals(emptyList(), EmptyBrowserDiscovery("Linux").discover())
        assertEquals(emptyList(), EmptyBrowserDiscovery("Windows 11").discover())
        assertEquals(emptyList(), EmptyBrowserDiscovery("").discover())
    }
}
