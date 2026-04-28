package dev.hackathon.linkopener.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class BrowserId(val value: String)

// Keyed by applicationPath (not bundleId) so two installs of the same browser
// at different paths can be exclusion-toggled independently. From stage 046
// onward, Chromium browsers expanded into per-profile entries get a
// profile-suffixed id `<applicationPath>#<profileDir>` — that way each
// profile is a first-class participant in exclusions / order / rules. Browsers
// without a profile (single-profile Chromium, Safari, manual-add) keep the
// plain path form for backward compat with persisted ids written before 046.
fun Browser.toBrowserId(): BrowserId =
    if (profile != null) BrowserId("$applicationPath#${profile.id}")
    else BrowserId(applicationPath)
