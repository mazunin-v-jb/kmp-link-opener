package dev.hackathon.linkopener.platform.android

/**
 * Pulls a usable URL out of the data carried by either an
 * `ACTION_VIEW http(s)://…` intent (the standard browser entry) or an
 * `ACTION_SEND text/plain` intent (Android share sheets).
 *
 * Pure function on primitives — kept separate from `PickerActivity` so
 * unit tests don't need Robolectric to construct an `Intent`. The
 * activity adapter at the call site does the
 * `intent → (action, dataString, extraText)` deconstruction.
 *
 * Returns null when neither shape carries a usable URL.
 *
 * URL detection in `EXTRA_TEXT`: share-sheet payloads vary widely
 * ("Check this https://x.example", just the URL, an article preview
 * with embedded URL, etc.). We grab the first http(s) match. Loose
 * regex on purpose — RFC 3986 strictness would reject perfectly valid
 * share payloads. Stops at whitespace.
 */
fun extractUrlFromIntentParts(
    action: String?,
    dataString: String?,
    extraText: String?,
): String? {
    if (dataString != null) return dataString
    if (action == ACTION_SEND && extraText != null) {
        return URL_PATTERN.find(extraText)?.value
    }
    return null
}

// Mirror of `Intent.ACTION_SEND` so the extraction logic stays free of
// the Android framework dependency — testable as a plain Kotlin function.
private const val ACTION_SEND: String = "android.intent.action.SEND"

private val URL_PATTERN: Regex = Regex(
    """https?://\S+""",
    RegexOption.IGNORE_CASE,
)
