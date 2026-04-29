package dev.hackathon.linkopener.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 32dp tinted square showing a browser's first-letter initial. Shared by the
 * Settings browser list and the picker popup so a design-system tweak only
 * touches one place.
 *
 * Two variants:
 * - **Plain** (default) — flat tinted square, used in the Settings exclusions
 *   list where rows already sit inside a bordered container.
 * - **Bordered** — slightly stronger background tint plus a 1dp outline,
 *   used in the picker where rows aren't enclosed in a card. The bordered
 *   variant also uses a smaller initial (14sp) to balance the extra outline.
 */
@Composable
internal fun BrowserAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    bordered: Boolean = false,
) {
    val alpha = if (bordered) 0.18f else 0.15f
    val baseModifier = modifier
        .size(32.dp)
        .background(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
            shape = RoundedCornerShape(6.dp),
        )
    val finalModifier = if (bordered) {
        baseModifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(6.dp),
        )
    } else {
        baseModifier
    }
    Box(modifier = finalModifier, contentAlignment = Alignment.Center) {
        val style = if (bordered) {
            MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } else {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        }
        Text(
            text = initial,
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
