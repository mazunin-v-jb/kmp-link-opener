package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserOrderingTest {

    private fun browser(id: String): Browser =
        Browser(bundleId = "bundle.$id", displayName = id, applicationPath = "/Applications/$id.app", version = null)

    @Test
    fun emptyOrderKeepsDiscoveryOrder() {
        val list = listOf(browser("Safari"), browser("Chrome"), browser("Firefox"))

        assertEquals(list, applyUserOrder(list, emptyList()))
    }

    @Test
    fun fullOrderRespected() {
        val safari = browser("Safari")
        val chrome = browser("Chrome")
        val firefox = browser("Firefox")
        val order = listOf(firefox.toBrowserId(), safari.toBrowserId(), chrome.toBrowserId())

        val result = applyUserOrder(listOf(safari, chrome, firefox), order)

        assertEquals(listOf(firefox, safari, chrome), result)
    }

    @Test
    fun unknownIdsInOrderAreSkipped() {
        val safari = browser("Safari")
        val chrome = browser("Chrome")
        val order = listOf(BrowserId("/Applications/Edge.app"), safari.toBrowserId())

        val result = applyUserOrder(listOf(safari, chrome), order)

        // Edge isn't installed → skipped. Safari moves to the front; Chrome (not in order) tails along.
        assertEquals(listOf(safari, chrome), result)
    }

    @Test
    fun newlyDiscoveredBrowsersAppendInDiscoveryOrder() {
        val safari = browser("Safari")
        val chrome = browser("Chrome")
        val firefox = browser("Firefox")
        // User pinned Safari ahead of Chrome before Firefox got installed.
        val order = listOf(safari.toBrowserId(), chrome.toBrowserId())

        val result = applyUserOrder(listOf(chrome, firefox, safari), order)

        // Safari + Chrome respect the pinned order; Firefox keeps its discovery slot at the tail.
        assertEquals(listOf(safari, chrome, firefox), result)
    }
}
