package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

/**
 * High-level family of a browser, used at runtime to pick the right profile
 * detector and launcher flag. Detection happens at discovery time
 * ([dev.hackathon.linkopener.platform.macos.MacOsBrowserDiscovery]) and the
 * value rides along on the [Browser] for the rest of the graph.
 *
 * Single source of truth for the bundleId → family mapping is
 * `dev.hackathon.linkopener.domain.ChromiumUserDataDirs`.
 */
@Serializable
enum class BrowserFamily {
    /** Chrome, Edge, Brave, Vivaldi, Opera, Chromium, ... */
    Chromium,

    /** Firefox — placeholder; profile detection not implemented in MVP. */
    Firefox,

    /** Safari — placeholder; profile data is opaque (CloudKit). */
    Safari,

    /** Anything we don't recognise. No profile expansion attempted. */
    Other,
}
