package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// AWT TrayIcon scales whatever BufferedImage it's given down to the
// platform's tray slot (~22×22 logical on macOS, ~16×16 on Windows) using
// a low-quality nearest-neighbor algorithm. Going from a 1024×1024 source
// straight to that size produces crunchy aliasing on fine line art.
//
// We pre-scale the source ourselves with Graphics2D bicubic interpolation
// and quality rendering hints to 2× the system tray's logical size so the
// HiDPI panel renders crisp; AWT then only needs a small downscale.

private const val APP_ICON_RESOURCE = "icons/app_icon.png"
private const val FALLBACK_TRAY_LOGICAL_SIZE = 22
private const val TRAY_HIDPI_FACTOR = 2

internal fun loadTrayIconPainter(): Painter {
    val target = computeTrayRenderSize()
    val scaled = highQualityScale(readResource(APP_ICON_RESOURCE), target, target)
    return BitmapPainter(scaled.toComposeImageBitmap())
}

internal fun loadAppIconPainter(): Painter {
    // Window decoration / dock icon can keep the full-resolution source;
    // the OS handles its own high-quality scaling for those slots.
    return BitmapPainter(readResource(APP_ICON_RESOURCE).toComposeImageBitmap())
}

private fun computeTrayRenderSize(): Int {
    val logical = runCatching {
        if (SystemTray.isSupported()) SystemTray.getSystemTray().trayIconSize.width
        else FALLBACK_TRAY_LOGICAL_SIZE
    }.getOrDefault(FALLBACK_TRAY_LOGICAL_SIZE)
    val safe = logical.takeIf { it > 0 } ?: FALLBACK_TRAY_LOGICAL_SIZE
    return safe * TRAY_HIDPI_FACTOR
}

private fun highQualityScale(source: BufferedImage, width: Int, height: Int): BufferedImage {
    val target = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = target.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source, 0, 0, width, height, null)
    } finally {
        g.dispose()
    }
    return target
}

private fun readResource(resource: String): BufferedImage {
    val loader = TrayIconLoaderClassLoaderHolder.loader
    val stream = loader.getResourceAsStream(resource)
        ?: error("Resource not found on classpath: $resource")
    return stream.use { ImageIO.read(it) ?: error("Cannot decode resource: $resource") }
}

private object TrayIconLoaderClassLoaderHolder {
    val loader: ClassLoader = TrayIconLoaderClassLoaderHolder::class.java.classLoader
}
