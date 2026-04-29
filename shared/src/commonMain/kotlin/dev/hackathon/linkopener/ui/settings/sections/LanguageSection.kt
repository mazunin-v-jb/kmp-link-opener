package dev.hackathon.linkopener.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hackathon.linkopener.core.model.AppLanguage
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.components.EnumDropdown
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import dev.hackathon.linkopener.ui.strings.languageLabel
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.app_language
import kmp_link_opener.shared.generated.resources.section_language
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LanguageSection(
    current: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_language), AppIcons.Translate) {
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EnumDropdown(
                    value = current,
                    options = AppLanguage.entries,
                    optionLabel = { languageLabel(it) },
                    onSelected = onSelected,
                )
            }
        }
    }
}
