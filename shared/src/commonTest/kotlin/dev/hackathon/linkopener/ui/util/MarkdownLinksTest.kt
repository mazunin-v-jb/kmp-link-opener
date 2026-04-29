package dev.hackathon.linkopener.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownLinksTest {

    @Test
    fun plainTextIsAppendedVerbatimWithNoLinks() {
        val result = parseMarkdownLinks("just text, nothing fancy", Color.Blue)

        assertEquals("just text, nothing fancy", result.text)
        assertEquals(0, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun singleLinkBecomesUrlAnnotation() {
        val result = parseMarkdownLinks(
            "Click [here](https://example.com) please",
            Color.Blue,
        )

        assertEquals("Click here please", result.text)
        val annotations = result.getLinkAnnotations(0, result.length)
        assertEquals(1, annotations.size)
        val link = annotations.single().item as LinkAnnotation.Url
        assertEquals("https://example.com", link.url)
        // Annotation range covers exactly "here" (chars 6-9 inclusive,
        // [start, end) → 6..10).
        assertEquals(6, annotations.single().start)
        assertEquals(10, annotations.single().end)
    }

    @Test
    fun multipleLinksParseInOrderWithLiteralRunsBetween() {
        val result = parseMarkdownLinks(
            "Pick a [Pull Request](https://gh/pr) or an [Issue](https://gh/i) — your call",
            Color.Blue,
        )

        assertEquals(
            "Pick a Pull Request or an Issue — your call",
            result.text,
        )
        val annotations = result.getLinkAnnotations(0, result.length)
        assertEquals(2, annotations.size)
        assertEquals("https://gh/pr", (annotations[0].item as LinkAnnotation.Url).url)
        assertEquals("https://gh/i", (annotations[1].item as LinkAnnotation.Url).url)
    }

    @Test
    fun emptyStringYieldsEmptyAnnotated() {
        val result = parseMarkdownLinks("", Color.Blue)

        assertEquals("", result.text)
        assertEquals(0, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun unmatchedBracketsAreLeftLiteral() {
        // No `]` to close — regex won't match, run goes through as plain text.
        val result = parseMarkdownLinks(
            "Not a link [no closing bracket(https://example.com) here",
            Color.Blue,
        )

        assertEquals(
            "Not a link [no closing bracket(https://example.com) here",
            result.text,
        )
        assertEquals(0, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun linkAtStartAndEndOfString() {
        val result = parseMarkdownLinks(
            "[start](https://a) middle [end](https://b)",
            Color.Blue,
        )

        assertEquals("start middle end", result.text)
        val annotations = result.getLinkAnnotations(0, result.length)
        assertEquals(2, annotations.size)
        assertEquals(0, annotations[0].start)
        assertEquals(13, annotations[1].start)
    }
}
