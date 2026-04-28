package dev.hackathon.linkopener.app

import androidx.compose.ui.window.application
import dev.hackathon.linkopener.app.tray.TrayHost

fun main() {
    val container = AppContainer()
    container.urlReceiver.start { url ->
        println("[URL] received: $url")
    }
    application {
        TrayHost(container = container, onExit = ::exitApplication)
    }
}
