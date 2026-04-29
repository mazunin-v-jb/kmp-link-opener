package dev.hackathon.linkopener.app

import androidx.compose.ui.window.application
import dev.hackathon.linkopener.app.tray.TrayHost
import dev.hackathon.linkopener.app.tray.enableMacOsTrayTemplateImages
import kotlin.system.exitProcess

fun main() {
    // Has to run before the first TrayIcon is constructed so the macOS
    // CImage path picks up the flag — see JDK-8252015. Per-image properties
    // don't survive CTrayIcon's internal BufferedImage redraw; this is the
    // only API that reliably makes the icon track the menu-bar appearance.
    enableMacOsTrayTemplateImages()

    // Single-instance check before anything else: if another copy is already
    // running, ping it so it can react (open Settings / show a nudge) and
    // bail out of this JVM. Stays in scope so its listener thread keeps
    // accepting activation pings for the lifetime of the process.
    val guard = SingleInstanceGuard.acquireOrSignal() ?: exitProcess(0)

    val container = AppContainer()
    guard.onActivationRequest = { container.requestActivation() }

    container.urlReceiver.start { url ->
        container.pickerCoordinator.handleIncomingUrl(url)
    }
    application {
        TrayHost(container = container, onExit = ::exitApplication)
    }
}
