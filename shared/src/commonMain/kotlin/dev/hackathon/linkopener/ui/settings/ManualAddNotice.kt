package dev.hackathon.linkopener.ui.settings

/**
 * Last non-success result from a manual-browser add attempt, surfaced to the
 * Settings UI so the user knows *why* nothing appeared in the list. `null`
 * means there's no pending message (cleared on dismiss or on the next
 * successful add).
 */
sealed interface ManualAddNotice {
    data object Duplicate : ManualAddNotice
    data object IsSelf : ManualAddNotice
    data class InvalidApp(val reason: String) : ManualAddNotice
}
