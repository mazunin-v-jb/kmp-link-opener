package dev.hackathon.linkopener.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import dev.hackathon.linkopener.app.tray.TrayHost

fun main() = application {
    val container = remember { AppContainer() }
    TrayHost(
        container = container,
        onExit = ::exitApplication,
    )
}
