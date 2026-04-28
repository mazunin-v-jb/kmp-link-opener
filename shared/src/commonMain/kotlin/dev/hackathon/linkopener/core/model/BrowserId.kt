package dev.hackathon.linkopener.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class BrowserId(val value: String)

// Keyed by applicationPath, not bundleId: two installs of the same browser
// (e.g. /Applications/Google Chrome.app and a second copy elsewhere) share a
// bundleId, and we want exclusions to apply per-installation. Discovery
// already de-dupes by applicationPath, so this matches the identity used
// upstream.
fun Browser.toBrowserId(): BrowserId = BrowserId(applicationPath)
