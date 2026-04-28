package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.toBrowserId

/**
 * Applies a user-defined ordering to a list of browsers.
 *
 * Browsers whose id appears in [order] come first, in the same order as [order].
 * Browsers not in [order] (e.g. newly discovered) keep their original
 * relative position and are appended after the ordered ones.
 * Ids in [order] that don't match any browser in [browsers] are silently skipped.
 */
fun applyUserOrder(browsers: List<Browser>, order: List<BrowserId>): List<Browser> {
    if (order.isEmpty()) return browsers
    val byId = browsers.associateBy { it.toBrowserId() }
    val claimed = mutableSetOf<BrowserId>()
    val ordered = mutableListOf<Browser>()
    for (id in order) {
        val b = byId[id] ?: continue
        ordered += b
        claimed += id
    }
    for (b in browsers) {
        if (b.toBrowserId() !in claimed) ordered += b
    }
    return ordered
}
