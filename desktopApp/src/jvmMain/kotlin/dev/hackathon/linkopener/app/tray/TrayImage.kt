package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.hackathon.linkopener.platform.HostOs
import java.awt.image.BufferedImage
import java.awt.image.MultiResolutionImage

internal const val MAC_OS_TEMPLATE_PROPERTY: String = "apple.awt.enableTemplateImages"

// Pixel size we hand to AWT — logical tray slot times a hi-DPI factor of 2
// so retina menu bars on macOS / scaled Windows displays don't crunch fine
// line art on downscale. Skia rasterizes the painter at exactly this pixel
// size; we don't rely on AWT's later resolution-variant requests because we
// need a concrete BufferedImage for TrayIcon's image slot.
private const val MAC_OS_TRAY_PIXEL_SIZE: Int = 44   // 22pt × 2
private const val LINUX_TRAY_PIXEL_SIZE: Int = 44
private const val WINDOWS_TRAY_PIXEL_SIZE: Int = 32  // 16pt × 2
private const val FALLBACK_TRAY_PIXEL_SIZE: Int = 44

/**
 * Process-wide opt-in for macOS template tray icons (JDK-8252015). Once set,
 * every `java.awt.TrayIcon` created in this JVM has its image treated as a
 * template by AppKit — the system tints it monochrome to match the menu-bar
 * appearance (white in dark mode, black in light mode), and the asset itself
 * just needs to be a black-on-transparent alpha mask.
 *
 * Must be set **before** the first `TrayIcon` is constructed: AppKit only
 * reads the flag when it builds the underlying `NSImage`. Per-image
 * `BufferedImage` properties don't work here because `CTrayIcon` internally
 * redraws the source image into a fresh BufferedImage (so the property would
 * be discarded) — this system property is the only reliable hook.
 *
 * No-op on Windows/Linux, where the JDK ignores the flag.
 */
internal fun enableMacOsTrayTemplateImages() {
    System.setProperty(MAC_OS_TEMPLATE_PROPERTY, "true")
}

/**
 * Rasterize [painter] to a [BufferedImage] sized for [hostOs]'s tray slot.
 * Template-image tinting on macOS is handled process-wide by
 * [enableMacOsTrayTemplateImages], so this function is just a vector → raster
 * shim — no per-image flag.
 */
internal fun prepareTrayImage(
    painter: Painter,
    hostOs: HostOs,
): BufferedImage {
    val pixelSize = trayPixelSize(hostOs)
    // Painter.toAwtImage returns a lazy MultiResolutionImage that defers
    // rasterization until AWT calls getResolutionVariant. We need a concrete
    // BufferedImage now (TrayIcon's image slot expects it), so we force a
    // single resolution variant at the exact pixel size we want.
    val multi = painter.toAwtImage(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        size = Size(pixelSize.toFloat(), pixelSize.toFloat()),
    ) as MultiResolutionImage
    return multi.getResolutionVariant(
        pixelSize.toDouble(),
        pixelSize.toDouble(),
    ) as BufferedImage
}

private fun trayPixelSize(hostOs: HostOs): Int = when (hostOs) {
    HostOs.MacOs -> MAC_OS_TRAY_PIXEL_SIZE
    HostOs.Linux -> LINUX_TRAY_PIXEL_SIZE
    HostOs.Windows -> WINDOWS_TRAY_PIXEL_SIZE
    HostOs.Other -> FALLBACK_TRAY_PIXEL_SIZE
}
