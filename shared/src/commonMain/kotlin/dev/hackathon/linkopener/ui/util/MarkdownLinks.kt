package dev.hackathon.linkopener.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * Tiny one-shot parser for `[label](url)` tokens inside a string. Returns an
 * [AnnotatedString] where the matched runs become [LinkAnnotation.Url] spans
 * (Compose 1.7+ — clicking opens the URL via the platform handler), and
 * everything else is appended as-is. Anything that doesn't match the
 * `[label](url)` shape (mismatched brackets, nested links, escaped chars)
 * is left literal — there's no full markdown grammar here, just enough to
 * hand-craft the help-dialog body in the strings.xml without splitting it
 * across many resources.
 *
 * Link runs are coloured with [linkColor] and underlined; no other styling.
 */
fun parseMarkdownLinks(text: String, linkColor: Color): AnnotatedString {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        var lastEnd = 0
        LINK_REGEX.findAll(text).forEach { match ->
            append(text.substring(lastEnd, match.range.first))
            val (label, url) = match.destructured
            withLink(LinkAnnotation.Url(url, styles = linkStyle)) {
                append(label)
            }
            lastEnd = match.range.last + 1
        }
        append(text.substring(lastEnd))
    }
}

// `[label](url)` — label can't contain `]`, url can't contain `)`. Greedy
// enough for our hand-curated strings.
private val LINK_REGEX = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
