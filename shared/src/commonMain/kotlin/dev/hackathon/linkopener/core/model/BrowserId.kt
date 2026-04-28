package dev.hackathon.linkopener.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class BrowserId(val value: String)
