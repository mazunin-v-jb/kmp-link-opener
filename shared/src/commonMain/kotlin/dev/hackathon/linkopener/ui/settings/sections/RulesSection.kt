package dev.hackathon.linkopener.ui.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.core.model.toBrowserId
import dev.hackathon.linkopener.core.model.uiLabel
import dev.hackathon.linkopener.ui.icons.AppIcons
import dev.hackathon.linkopener.ui.settings.components.SectionCard
import dev.hackathon.linkopener.ui.settings.components.SectionPane
import dev.hackathon.linkopener.ui.theme.surfaceContainerLow
import dev.hackathon.linkopener.ui.theme.surfaceContainerLowest
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.add_rule
import kmp_link_opener.shared.generated.resources.move_down
import kmp_link_opener.shared.generated.resources.move_up
import kmp_link_opener.shared.generated.resources.rule_no_browsers
import kmp_link_opener.shared.generated.resources.rule_no_rules
import kmp_link_opener.shared.generated.resources.rule_pattern_placeholder
import kmp_link_opener.shared.generated.resources.rule_remove_content_description
import kmp_link_opener.shared.generated.resources.section_rules
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RulesSection(
    rules: List<UrlRule>,
    availableBrowsers: List<Browser>,
    onAddRule: (pattern: String, browserId: BrowserId) -> Unit,
    onRemoveRule: (index: Int) -> Unit,
    onMoveRule: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateRulePattern: (index: Int, pattern: String) -> Unit,
    onUpdateRuleBrowser: (index: Int, browserId: BrowserId) -> Unit,
) {
    SectionPane(stringResource(Res.string.section_rules), AppIcons.Rules) {
        if (availableBrowsers.isEmpty()) {
            SectionCard {
                Text(
                    text = stringResource(Res.string.rule_no_browsers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SectionPane
        }
        if (rules.isEmpty()) {
            SectionCard {
                Text(
                    text = stringResource(Res.string.rule_no_rules),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = surfaceContainerLowest(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rules.forEachIndexed { index, rule ->
                    RuleRow(
                        rule = rule,
                        availableBrowsers = availableBrowsers,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        onPatternChange = { onUpdateRulePattern(index, it) },
                        onBrowserChange = { onUpdateRuleBrowser(index, it) },
                        onMoveUp = { onMoveRule(index, index - 1) },
                        onMoveDown = { onMoveRule(index, index + 1) },
                        onRemove = { onRemoveRule(index) },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = {
                // Default to the first available browser; user can pick another in the dropdown.
                val firstBrowser = availableBrowsers.first().toBrowserId()
                onAddRule("", firstBrowser)
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.add_rule),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: UrlRule,
    availableBrowsers: List<Browser>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onPatternChange: (String) -> Unit,
    onBrowserChange: (BrowserId) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pattern field
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = surfaceContainerLow(),
                    shape = RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = rule.pattern,
                onValueChange = onPatternChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (rule.pattern.isEmpty()) {
                Text(
                    text = stringResource(Res.string.rule_pattern_placeholder),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "→",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        // Browser dropdown
        BrowserDropdown(
            current = rule.browserId,
            options = availableBrowsers,
            onSelected = onBrowserChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = AppIcons.ArrowUp,
                contentDescription = stringResource(Res.string.move_up),
                tint = if (canMoveUp) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = AppIcons.ArrowDown,
                contentDescription = stringResource(Res.string.move_down),
                tint = if (canMoveDown) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = AppIcons.Close,
                contentDescription = stringResource(Res.string.rule_remove_content_description),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun BrowserDropdown(
    current: BrowserId,
    options: List<Browser>,
    onSelected: (BrowserId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentBrowser = options.firstOrNull { it.toBrowserId() == current }
    val label = currentBrowser?.uiLabel ?: current.value.substringAfterLast('/')
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = LocalContentColor.current,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = AppIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { browser ->
                DropdownMenuItem(
                    text = { Text(browser.uiLabel) },
                    onClick = {
                        onSelected(browser.toBrowserId())
                        expanded = false
                    },
                )
            }
        }
    }
}
