package dev.hackathon.linkopener.app

import androidx.compose.ui.window.application
import dev.hackathon.linkopener.app.tray.TrayHost
import dev.hackathon.linkopener.app.tray.enableMacOsTrayTemplateImages
import dev.hackathon.linkopener.platform.HostOs
import dev.hackathon.linkopener.platform.PlatformFactory
import dev.hackathon.linkopener.platform.windows.WindowsHandlerRegistration
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Has to run before the first TrayIcon is constructed so the macOS
    // CImage path picks up the flag — see JDK-8252015. Per-image properties
    // don't survive CTrayIcon's internal BufferedImage redraw; this is the
    // only API that reliably makes the icon track the menu-bar appearance.
    enableMacOsTrayTemplateImages()

    // Pick the first http/https argv as a URL to open. Windows spawns
    // us as `LinkOpener.exe <url>` once registered as the URL handler;
    // macOS uses Launch Services' OpenURIHandler instead so this path
    // is Windows-only in practice. Other argv (e.g. JVM `--add-opens`
    // and friends, or anything else the OS prepends) get ignored.
    val urlFromArgs = args.firstOrNull {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    val container = AppContainer()
    // Held for the lifetime of the process — dropping the reference would
    // let the listener thread's strong-ref-only ServerSocket get GC'd and
    // stop accepting activation pings.
    @Suppress("UNUSED_VARIABLE")
    val guard = SingleInstanceGuard.acquireOrSignal(urlPayload = urlFromArgs) ?: exitProcess(0)

    // We're the primary. Activation requests now carry an optional URL;
    // a URL-bearing ping comes from a secondary spawned by the Windows
    // URL handler — push it straight into the picker. URL-less pings
    // are bare "wake the Settings window" requests (the existing
    // SingleInstanceGuard pattern).
    guard.onActivationRequest = { url ->
        if (url != null) container.pickerCoordinator.handleIncomingUrl(url)
        else container.requestActivation()
    }

    // Register ourselves as a URL handler on Windows (HKCU writes, no
    // elevation). Idempotent so running every startup is fine. macOS's
    // analogue is the Info.plist's CFBundleURLTypes registered at .app
    // install time; Linux is stage 8.
    if (PlatformFactory.currentOs == HostOs.Windows) {
        runBlocking {
            WindowsHandlerRegistration().register()
        }
    }

    // If we WERE handed a URL on argv (Windows secondary that just
    // happened to also be the primary — e.g. first launch via a click),
    // push it into the picker now that we have a container.
    urlFromArgs?.let { container.pickerCoordinator.handleIncomingUrl(it) }

    container.urlReceiver.start { url ->
        container.pickerCoordinator.handleIncomingUrl(url)
    }
    application {
        TrayHost(container = container, onExit = ::exitApplication)
    }
}
