package dev.hackathon.linkopener.ui.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.hackathon.linkopener.ui.util.parseMarkdownLinks
import kmp_link_opener.shared.generated.resources.Res
import kmp_link_opener.shared.generated.resources.help_dialog_body
import kmp_link_opener.shared.generated.resources.help_dialog_close
import kmp_link_opener.shared.generated.resources.help_dialog_title
import org.jetbrains.compose.resources.stringResource

/**
 * Modal "About / Help" dialog. The body string lives in `strings.xml` with
 * inline `[label](url)` tokens that [parseMarkdownLinks] turns into
 * clickable [androidx.compose.ui.text.LinkAnnotation.Url] spans — opening
 * each URL goes through the platform's default URL handler (which on a
 * machine where Link Opener IS the default browser will route through our
 * own picker, charmingly).
 */
@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    val title = stringResource(Res.string.help_dialog_title)
    val body = stringResource(Res.string.help_dialog_body)
    val closeLabel = stringResource(Res.string.help_dialog_close)
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedBody = parseMarkdownLinks(body, linkColor)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = annotatedBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(closeLabel)
                    }
                }
            }
        }
    }
}
