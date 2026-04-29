package dev.hackathon.linkopener.platform

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cross-platform-ish icon loader backed by Swing's [FileSystemView]. Works
 * well on **macOS** (Finder hands back the `.app` bundle's icon, including
 * the modern rounded-rect treatment) and **Windows** (Shell32 hands back the
 * embedded `.exe` resource icon). Linux returns generic mimetype icons here
 * — the proper XDG icon-theme walk lives in
 * [dev.hackathon.linkopener.platform.linux.LinuxBrowserIconLoader].
 *
 * Uses the JDK 9+ `getSystemIcon(File, w, h)` overload so we get a sized
 * variant suitable for HiDPI rather than the ancient 16×16 default. The
 * returned [javax.swing.Icon] is painted into a [BufferedImage] and encoded
 * to PNG so the cross-platform [BrowserIconLoader] contract stays
 * platform-agnostic at the byte level.
 */
internal class FileSystemViewBrowserIconLoader(
    private val targetSize: Int = DEFAULT_TARGET_SIZE,
) : BrowserIconLoader {

    override suspend fun load(applicationPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(applicationPath)
        if (!file.exists()) return@withContext null

        val icon = runCatching {
            FileSystemView.getFileSystemView().getSystemIcon(file, targetSize, targetSize)
        }.getOrNull() ?: return@withContext null

        val width = icon.iconWidth.takeIf { it > 0 } ?: return@withContext null
        val height = icon.iconHeight.takeIf { it > 0 } ?: return@withContext null

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }

        encodeAsPng(image)
    }

    private fun encodeAsPng(image: BufferedImage): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (ImageIO.write(image, "png", out)) out.toByteArray() else null
    }

    companion object {
        // Single-resolution snapshot at 64dp-equivalent — tray and settings rows
        // top out at 32dp display, so 64px gives 2× retina headroom while
        // keeping the cached byte count down to ~4-12 KB per browser.
        const val DEFAULT_TARGET_SIZE: Int = 64
    }
}
