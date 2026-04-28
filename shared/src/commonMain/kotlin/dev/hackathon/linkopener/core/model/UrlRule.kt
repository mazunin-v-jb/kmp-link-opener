package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

/**
 * One user-defined rule: when an incoming URL's host matches [pattern]
 * (per [dev.hackathon.linkopener.domain.HostGlobMatcher]), the link is
 * opened directly in the browser identified by [browserId], bypassing the
 * picker. Stored as part of [AppSettings.rules]; ordering in that list
 * is the user's priority order (first match wins).
 */
@Serializable
data class UrlRule(
    val pattern: String,
    val browserId: BrowserId,
)
