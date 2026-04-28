package dev.hackathon.linkopener.platform

interface AutoStartManager {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun isEnabled(): Boolean
}
