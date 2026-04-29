package dev.hackathon.linkopener.platform.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure-function tests for [extractUrlFromIntentParts]. No Robolectric
 * needed — the function takes primitives, the activity adapter does
 * the `intent → (action, dataString, extraText)` deconstruction.
 *
 * Covers both the ACTION_VIEW path (browser default-handler entry) and
 * the ACTION_SEND path (share-sheet "Share to Link Opener" with various
 * payload shapes).
 */
class IntentUrlExtractorTest {

    @Test
    fun returnsDataStringForActionView() {
        // Standard tap-link-from-app entry: dataString carries the URL,
        // we hand it back verbatim.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.VIEW",
            dataString = "https://example.com/foo",
            extraText = null,
        )
        assertEquals("https://example.com/foo", url)
    }

    @Test
    fun preservesQueryAndFragmentInDataString() {
        // Defensive: percent-encoding, query strings, fragments must all
        // round-trip to the launcher unchanged. No regex, just hand back.
        val complex = "https://example.com/path/?q=hello%20world&x=1#frag"
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.VIEW",
            dataString = complex,
            extraText = null,
        )
        assertEquals(complex, url)
    }

    @Test
    fun preservesHttpVariant() {
        // Plain http (not just https) should also round-trip.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.VIEW",
            dataString = "http://example.com",
            extraText = null,
        )
        assertEquals("http://example.com", url)
    }

    @Test
    fun extractsBareUrlFromActionSendExtraText() {
        // Most common share-sheet payload shape: just the URL.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "https://example.com/article",
        )
        assertEquals("https://example.com/article", url)
    }

    @Test
    fun extractsFirstUrlFromMessyShareText() {
        // "Share with prefix" pattern: app prepends "Look at this:" or
        // similar before the URL. We grab the first http(s) match.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "Check this out: https://example.com/great-article",
        )
        assertEquals("https://example.com/great-article", url)
    }

    @Test
    fun stopsAtWhitespaceWhenSearchingShareText() {
        // Share payload with the URL embedded in a longer sentence.
        // Regex is greedy on non-whitespace, so it must stop at the
        // following space before "is".
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "https://example.com/x is a cool site",
        )
        assertEquals("https://example.com/x", url)
    }

    @Test
    fun extractsHttpUrlFromShareText() {
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "old-school link: http://archaic.example",
        )
        assertEquals("http://archaic.example", url)
    }

    @Test
    fun returnsFirstWhenShareTextHasMultipleUrls() {
        // If the user shares a message containing multiple URLs (rare
        // but possible — e.g. preview + canonical), we surface the
        // first one. Predictable behaviour beats trying to be clever.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "Compare https://first.example and https://second.example",
        )
        assertEquals("https://first.example", url)
    }

    @Test
    fun returnsNullForActionSendWithoutAnyUrl() {
        // Pure-text share with no http(s) token — nothing to launch.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "Just plain text, nothing to see here.",
        )
        assertNull(url)
    }

    @Test
    fun returnsNullForActionSendWithNullExtraText() {
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = null,
        )
        assertNull(url)
    }

    @Test
    fun returnsNullForUnknownActionWithNoDataString() {
        // Some intent we don't recognise (e.g. ACTION_PROCESS_TEXT) and
        // no data → bail. Not our shape.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.PROCESS_TEXT",
            dataString = null,
            extraText = "https://example.com",
        )
        assertNull(url)
    }

    @Test
    fun dataStringWinsOverExtraText() {
        // Defensive: if both are present, dataString is canonical (the
        // ACTION_VIEW filter keys on it). Don't double-handle.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.VIEW",
            dataString = "https://primary.example",
            extraText = "https://secondary.example",
        )
        assertEquals("https://primary.example", url)
    }

    @Test
    fun returnsNullForCompletelyEmptyIntent() {
        val url = extractUrlFromIntentParts(
            action = null,
            dataString = null,
            extraText = null,
        )
        assertNull(url)
    }

    @Test
    fun matchesUrlCaseInsensitively() {
        // Some apps capitalise the scheme — "HTTPS://example.com". Our
        // regex flag is IGNORE_CASE, so this should still match.
        val url = extractUrlFromIntentParts(
            action = "android.intent.action.SEND",
            dataString = null,
            extraText = "Check HTTPS://EXAMPLE.COM/foo",
        )
        assertEquals("HTTPS://EXAMPLE.COM/foo", url)
    }
}
