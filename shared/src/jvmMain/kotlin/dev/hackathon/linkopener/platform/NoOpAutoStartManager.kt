package dev.hackathon.linkopener.platform

class NoOpAutoStartManager : AutoStartManager {
    override suspend fun isEnabled(): Boolean = false
    override suspend fun setEnabled(enabled: Boolean) = Unit
}
