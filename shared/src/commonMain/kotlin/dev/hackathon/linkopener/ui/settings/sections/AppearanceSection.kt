package dev.hackathon.linkopener.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hackathon.linkopener.core.model.AppTheme
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.components.EnumDropdown
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import dev.hackathon.linkopener.ui.strings.themeLabel
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.section_appearance
import kmp_link_opener.shared.generated.resources.theme_mode
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AppearanceSection(
    current: AppTheme,
    onSelected: (AppTheme) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_appearance), AppIcons.Palette) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.theme_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppTheme.entries,
                    optionLabel = { themeLabel(it) },
                    onSelected = onSelected,
                )
            }
        }
    }
}
