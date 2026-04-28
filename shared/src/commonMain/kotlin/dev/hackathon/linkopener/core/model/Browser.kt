package dev.hackathon.linkopener.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Browser(
    val bundleId: String,
    val displayName: String,
    val applicationPath: String,
    val version: String?,
)
