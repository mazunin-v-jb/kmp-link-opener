package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

/**
 * One Chromium-family profile (or equivalent for other browser families,
 * once we support them). [id] is the on-disk directory name inside the
 * browser's user-data dir — it's what we hand back to the browser as
 * `--profile-directory=<id>`. [displayName] is what the user sees in the
 * picker / Settings — taken from `Local State`'s `profile.info_cache.<id>.name`.
 */
@Serializable
data class BrowserProfile(
    val id: String,
    val displayName: String,
)
