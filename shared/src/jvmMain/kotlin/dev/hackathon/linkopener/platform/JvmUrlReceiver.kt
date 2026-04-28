package dev.hackathon.linkopener.platform

import java.awt.Desktop

class JvmUrlReceiver : UrlReceiver {
    override fun start(onUrl: (String) -> Unit) {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.APP_OPEN_URI)) return
        desktop.setOpenURIHandler { event ->
            onUrl(event.uri.toString())
        }
    }
}
