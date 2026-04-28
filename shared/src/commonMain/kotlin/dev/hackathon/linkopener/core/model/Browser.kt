package dev.hackathon.linkopener.core.model

data class Browser(
    val bundleId: String,
    val displayName: String,
    val applicationPath: String,
    val version: String?,
)
