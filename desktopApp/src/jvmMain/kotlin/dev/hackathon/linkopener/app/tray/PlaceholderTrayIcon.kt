package dev.hackathon.linkopener.app.tray

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

// TODO: replace placeholder tray icon with the final designed asset.
// When ready, drop the PNG/SVG into desktopApp/src/jvmMain/resources/icons/
// and switch to androidx.compose.ui.res.painterResource("icons/app_tray_icon.png").
class PlaceholderTrayIcon(
    private val pixelSize: Float = 32f,
) : Painter() {

    override val intrinsicSize: Size = Size(pixelSize, pixelSize)

    override fun DrawScope.onDraw() {
        drawRect(color = Color(0xFF1F1F1F))
        val r = size.minDimension / 3f
        drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = size.minDimension / 12f),
        )
    }
}
