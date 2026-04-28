package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.core.model.toBrowserId

/**
 * Resolves an incoming URL against the user's rules. Single source of truth
 * for the rule-evaluation policy — this file owns four design decisions
 * from `ai_stages/06_custom_rules/plan.md`:
 *
 * - **#2 first-match-wins** lives in [findFirstApplicable]; swap the algorithm
 *   here to flip to most-specific-wins or any other ordering.
 * - **#3 match against host only** lives in [extractTarget]; replace it with
 *   the full URL (or any other component) to flip what the matcher sees.
 * - **#4 exclusion wins over rule** lives in the body of [resolve], in the
 *   `if (browserId in exclusions) skip` branch. Drop the branch to flip
 *   "rule wins regardless of exclusions".
 * - **#5 silently skip + debug log on missing browser** lives in the
 *   `if (browser == null)` branch. Replace `log` with a notice channel /
 *   thrown exception / etc. to flip the failure mode.
 */
class RuleEngine(
    private val matcher: (pattern: String, host: String) -> Boolean = HostGlobMatcher::matches,
    private val debug: Boolean = false,
    private val log: (String) -> Unit = ::println,
) {
    fun resolve(
        url: String,
        rules: List<UrlRule>,
        browsers: List<Browser>,
        exclusions: Set<BrowserId>,
    ): RuleDecision {
        val host = extractTarget(url) ?: return RuleDecision.Picker
        return findFirstApplicable(host, rules, browsers, exclusions)
    }

    private fun findFirstApplicable(
        host: String,
        rules: List<UrlRule>,
        browsers: List<Browser>,
        exclusions: Set<BrowserId>,
    ): RuleDecision {
        for (rule in rules) {
            if (!matcher(rule.pattern, host)) continue
            val browser = browsers.firstOrNull { it.toBrowserId() == rule.browserId }
            if (browser == null) {
                if (debug) {
                    log("[ruleEngine] rule '${rule.pattern}' → ${rule.browserId.value} skipped: browser not installed")
                }
                continue
            }
            if (rule.browserId in exclusions) {
                if (debug) {
                    log("[ruleEngine] rule '${rule.pattern}' → ${browser.displayName} skipped: browser is excluded")
                }
                continue
            }
            return RuleDecision.Direct(browser)
        }
        return RuleDecision.Picker
    }

    /**
     * Returns lowercased host of [url], or `null` for inputs that don't have
     * one (mailto:, malformed strings, etc.) — those fall through to the
     * picker since none of our rules can target them.
     *
     * Hand-rolled to stay in commonMain (no `java.net.URI`). Parses scheme,
     * userinfo, port, path/query/fragment off in that order.
     */
    private fun extractTarget(url: String): String? {
        val schemeEnd = url.indexOf("://").takeIf { it >= 0 } ?: return null
        var rest = url.substring(schemeEnd + 3)
        // Strip path / query / fragment.
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#'))
        if (authorityEnd >= 0) rest = rest.substring(0, authorityEnd)
        // Strip userinfo (`user:pass@`).
        val at = rest.lastIndexOf('@')
        if (at >= 0) rest = rest.substring(at + 1)
        // Strip port. IPv6 literals (`[::1]:8080`) — keep the bracket section.
        val host = if (rest.startsWith('[')) {
            val close = rest.indexOf(']')
            if (close < 0) rest else rest.substring(0, close + 1)
        } else {
            val colon = rest.indexOf(':')
            if (colon >= 0) rest.substring(0, colon) else rest
        }
        return host.lowercase().ifEmpty { null }
    }
}

sealed interface RuleDecision {
    data class Direct(val browser: Browser) : RuleDecision
    data object Picker : RuleDecision
}
