package dev.hackathon.linkopener.ui.icons

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_logo_v2
import kmp_link_opener.shared.generated.resources.appearance_fill
import kmp_link_opener.shared.generated.resources.appearance_line
import kmp_link_opener.shared.generated.resources.browser_exclusions_fill
import kmp_link_opener.shared.generated.resources.browser_exclusions_line
import kmp_link_opener.shared.generated.resources.close_fill
import kmp_link_opener.shared.generated.resources.close_line
import kmp_link_opener.shared.generated.resources.default_browser_fill
import kmp_link_opener.shared.generated.resources.default_browser_line
import kmp_link_opener.shared.generated.resources.help_fill
import kmp_link_opener.shared.generated.resources.help_line
import kmp_link_opener.shared.generated.resources.language_fill
import kmp_link_opener.shared.generated.resources.language_line
import kmp_link_opener.shared.generated.resources.reload_fill
import kmp_link_opener.shared.generated.resources.reload_line
import kmp_link_opener.shared.generated.resources.rules_fill
import kmp_link_opener.shared.generated.resources.rules_line
import kmp_link_opener.shared.generated.resources.system_fill
import kmp_link_opener.shared.generated.resources.system_line
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Section + utility icons live as theme-aware Painter accessors backed by the
// SVGs in composeResources/drawable/. Light theme uses the outlined "_line"
// variant; dark theme switches to the bolder "_fill" variant for better
// contrast against darker surfaces. SVGs use `currentColor`, so callers can
// keep using `Icon(painter, tint = ...)` and the path will pick up the tint.
//
// The remaining ImageVectors (Add, Search, ChevronDown, ArrowUp, ArrowDown)
// are kept hand-rolled — the new design pack does not include replacements.

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

object AppIcons {

    val DefaultBrowser: Painter
        @Composable get() = themed(Res.drawable.default_browser_line, Res.drawable.default_browser_fill)

    val Appearance: Painter
        @Composable get() = themed(Res.drawable.appearance_line, Res.drawable.appearance_fill)

    val Language: Painter
        @Composable get() = themed(Res.drawable.language_line, Res.drawable.language_fill)

    val System: Painter
        @Composable get() = themed(Res.drawable.system_line, Res.drawable.system_fill)

    val BrowserExclusions: Painter
        @Composable get() = themed(Res.drawable.browser_exclusions_line, Res.drawable.browser_exclusions_fill)

    val Rules: Painter
        @Composable get() = themed(Res.drawable.rules_line, Res.drawable.rules_fill)

    val Close: Painter
        @Composable get() = themed(Res.drawable.close_line, Res.drawable.close_fill)

    val Help: Painter
        @Composable get() = themed(Res.drawable.help_line, Res.drawable.help_fill)

    val Reload: Painter
        @Composable get() = themed(Res.drawable.reload_line, Res.drawable.reload_fill)

    // Single application logo asset shared across tray, window decoration
    // (Dock badge on macOS) and the Settings TopAppBar. No fill/line pair —
    // when used in `Icon(painter = …)` with a `tint`, Material's SrcIn filter
    // recolors the shape to match theme; in raw `Image`/`Tray` slots it
    // renders as-is.
    val AppLogoV2: Painter
        @Composable get() = painterResource(Res.drawable.app_logo_v2)

    @Composable
    private fun themed(line: DrawableResource, fill: DrawableResource): Painter =
        painterResource(if (isSystemInDarkTheme()) fill else line)

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

    val ChevronDown: ImageVector = line("ChevronDown") {
        stroke {
            moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        }
    }

    val ArrowUp: ImageVector = line("ArrowUp") {
        stroke {
            moveTo(12f, 19f); lineTo(12f, 5f)
            moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
        }
    }

    val ArrowDown: ImageVector = line("ArrowDown") {
        stroke {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
        }
    }

    // Six-dot 2×3 drag affordance — the platform-neutral "this row is
    // draggable" cue used by macOS, Material, and most Linux toolkits.
    // Drawn as filled circles so it tints cleanly via the consuming
    // `Icon(tint = …)`.
    val DragHandle: ImageVector = ImageVector.Builder(
        name = "DragHandle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero,
        ) {
            // Two columns of 3 dots at x=9 and x=15, y=6/12/18, radius 1.5.
            for (x in listOf(9f, 15f)) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(x - 1.5f, y)
                    arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x + 1.5f, y)
                    arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x - 1.5f, y)
                    close()
                }
            }
        }
    }.build()
}
