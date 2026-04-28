package dev.hackathon.linkopener.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Hand-rolled, line-style ImageVectors for the section icons + utility icons.
// Stroke-based on a 24x24 viewport so they read crisp at typical desktop
// densities. Drawn to evoke the same shapes as the Material Symbols used in
// the design mockups (palette / translate / settings_suggest / browser_updated)
// without pulling in the full material-icons-extended dependency.

private fun line(
    name: String,
    block: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()

private fun ImageVector.Builder.stroke(
    color: Color = Color.Black,
    width: Float = 2f,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) {
    path(
        fill = null,
        stroke = SolidColor(color),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )
}

private fun ImageVector.Builder.fill(
    color: Color = Color.Black,
    pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) {
    path(
        fill = SolidColor(color),
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )
}

object AppIcons {

    val Palette: ImageVector = line("Palette") {
        stroke {
            // Outer palette body (rounded blob)
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21f, 12f)
            curveTo(21f, 14.5f, 19f, 16f, 17f, 16f)
            horizontalLineTo(15f)
            curveTo(13.5f, 16f, 13f, 17f, 13f, 18f)
            curveTo(13f, 19.5f, 14f, 20f, 14f, 21f)
            curveTo(14f, 21.5f, 13f, 22f, 12f, 22f)
            curveTo(7f, 22f, 3f, 17.5f, 3f, 12f)
            curveTo(3f, 7f, 7f, 3f, 12f, 3f)
            close()
        }
        fill {
            // Color dots
            moveTo(7.5f, 11f); arcTo(1.25f, 1.25f, 0f, true, true, 7.5f, 13.5f); arcTo(1.25f, 1.25f, 0f, true, true, 7.5f, 11f); close()
            moveTo(11f, 7f); arcTo(1.25f, 1.25f, 0f, true, true, 11f, 9.5f); arcTo(1.25f, 1.25f, 0f, true, true, 11f, 7f); close()
            moveTo(15f, 7f); arcTo(1.25f, 1.25f, 0f, true, true, 15f, 9.5f); arcTo(1.25f, 1.25f, 0f, true, true, 15f, 7f); close()
            moveTo(18.5f, 11f); arcTo(1.25f, 1.25f, 0f, true, true, 18.5f, 13.5f); arcTo(1.25f, 1.25f, 0f, true, true, 18.5f, 11f); close()
        }
    }

    val Translate: ImageVector = line("Translate") {
        stroke {
            // Latin "A"
            moveTo(3f, 16f); lineTo(6f, 7f); lineTo(9f, 16f)
            moveTo(4f, 13f); lineTo(8f, 13f)
            // Asian glyph block (simplified)
            moveTo(13f, 7f); horizontalLineTo(21f)
            moveTo(17f, 7f); verticalLineTo(9f)
            moveTo(13f, 11f); horizontalLineTo(21f)
            moveTo(15f, 13f); curveTo(15f, 17f, 19f, 19f, 21f, 19f)
            moveTo(20f, 13f); curveTo(20f, 16f, 17f, 18f, 13f, 19f)
        }
    }

    val SettingsSuggest: ImageVector = line("SettingsSuggest") {
        stroke {
            // Gear (simplified 8-tooth)
            moveTo(12f, 8f); arcTo(4f, 4f, 0f, true, true, 12f, 16f); arcTo(4f, 4f, 0f, true, true, 12f, 8f); close()
            moveTo(12f, 3f); verticalLineTo(5f)
            moveTo(12f, 19f); verticalLineTo(21f)
            moveTo(3f, 12f); horizontalLineTo(5f)
            moveTo(19f, 12f); horizontalLineTo(21f)
            moveTo(5.6f, 5.6f); lineTo(7f, 7f)
            moveTo(17f, 17f); lineTo(18.4f, 18.4f)
            moveTo(5.6f, 18.4f); lineTo(7f, 17f)
            moveTo(17f, 7f); lineTo(18.4f, 5.6f)
        }
        fill {
            // Spark accents next to gear
            moveTo(20.5f, 4.5f); lineTo(21f, 3f); lineTo(21.5f, 4.5f); lineTo(23f, 5f); lineTo(21.5f, 5.5f); lineTo(21f, 7f); lineTo(20.5f, 5.5f); lineTo(19f, 5f); close()
        }
    }

    val BrowserUpdated: ImageVector = line("BrowserUpdated") {
        stroke {
            // Browser frame
            moveTo(3f, 6f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
            moveTo(3f, 9f); horizontalLineTo(21f)
            // Down-arrow inside
            moveTo(12f, 11.5f); verticalLineTo(16f)
            moveTo(9.5f, 13.5f); lineTo(12f, 16f); lineTo(14.5f, 13.5f)
        }
    }

    val Search: ImageVector = line("Search") {
        stroke {
            moveTo(11f, 4f); arcTo(7f, 7f, 0f, true, true, 11f, 18f); arcTo(7f, 7f, 0f, true, true, 11f, 4f); close()
            moveTo(16f, 16f); lineTo(21f, 21f)
        }
    }

    val Add: ImageVector = line("Add") {
        stroke(width = 2.4f) {
            moveTo(12f, 5f); verticalLineTo(19f)
            moveTo(5f, 12f); horizontalLineTo(19f)
        }
    }

    val Close: ImageVector = line("Close") {
        stroke(width = 2.2f) {
            moveTo(6f, 6f); lineTo(18f, 18f)
            moveTo(18f, 6f); lineTo(6f, 18f)
        }
    }

    val Help: ImageVector = line("Help") {
        stroke {
            moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 12f, 21f); arcTo(9f, 9f, 0f, true, true, 12f, 3f); close()
            moveTo(9.5f, 9.5f); curveTo(9.5f, 8f, 10.5f, 7f, 12f, 7f); curveTo(13.5f, 7f, 14.5f, 8f, 14.5f, 9.5f); curveTo(14.5f, 11f, 12f, 11.5f, 12f, 13.5f)
        }
        fill {
            moveTo(12f, 16f); arcTo(0.9f, 0.9f, 0f, true, true, 12f, 17.8f); arcTo(0.9f, 0.9f, 0f, true, true, 12f, 16f); close()
        }
    }

    val Settings: ImageVector = line("Settings") {
        stroke {
            moveTo(12f, 8.5f); arcTo(3.5f, 3.5f, 0f, true, true, 12f, 15.5f); arcTo(3.5f, 3.5f, 0f, true, true, 12f, 8.5f); close()
            moveTo(12f, 3f); verticalLineTo(5f)
            moveTo(12f, 19f); verticalLineTo(21f)
            moveTo(3f, 12f); horizontalLineTo(5f)
            moveTo(19f, 12f); horizontalLineTo(21f)
            moveTo(5.6f, 5.6f); lineTo(7f, 7f)
            moveTo(17f, 17f); lineTo(18.4f, 18.4f)
            moveTo(5.6f, 18.4f); lineTo(7f, 17f)
            moveTo(17f, 7f); lineTo(18.4f, 5.6f)
        }
    }

    val ChevronDown: ImageVector = line("ChevronDown") {
        stroke {
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        }
    }

    // TODO: replace this hand-rolled placeholder with the final design-system
    //  refresh glyph once the asset lands. Drawn here only so the Settings
    //  refresh button has a recognisable shape.
    val Refresh: ImageVector = line("Refresh") {
        stroke {
            // Three-quarter arc of a circle from (12, 4) clockwise around to
            // the bottom-left, leaving the gap in the upper-right where the
            // arrowhead sits.
            moveTo(12f, 4f)
            arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, 4.5f, 14f)
            // Arrowhead: chevron pointing up-right at the gap, indicating
            // "rotate clockwise" / refresh.
            moveTo(12f, 4f); lineTo(15.5f, 4f)
            moveTo(12f, 4f); lineTo(12f, 7.5f)
        }
    }
}
