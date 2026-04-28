package dev.hackathon.linkopener.domain

import dev.hackathon.linkopener.core.model.Browser
import dev.hackathon.linkopener.core.model.BrowserId
import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.core.model.toBrowserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleEngineTest {

    private val safari = Browser("com.apple.Safari", "Safari", "/Applications/Safari.app", "17.4")
    private val chrome = Browser("com.google.Chrome", "Chrome", "/Applications/Chrome.app", "124")
    private val firefox = Browser("org.mozilla.firefox", "Firefox", "/Applications/Firefox.app", "125")

    private fun ruleFor(browser: Browser, pattern: String) =
        UrlRule(pattern = pattern, browserId = browser.toBrowserId())

    @Test
    fun returnsPickerWhenNoRules() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://example.com",
            rules = emptyList(),
            browsers = listOf(safari, chrome),
            exclusions = emptySet(),
        )
        assertEquals(RuleDecision.Picker, decision)
    }

    @Test
    fun directWhenSingleMatchingRule() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://www.youtube.com/watch?v=abc",
            rules = listOf(ruleFor(firefox, "*.youtube.com")),
            browsers = listOf(safari, chrome, firefox),
            exclusions = emptySet(),
        )
        val direct = assertIs<RuleDecision.Direct>(decision)
        assertEquals(firefox, direct.browser)
    }

    @Test
    fun firstMatchWinsAcrossOverlappingRules() {
        val engine = RuleEngine()
        val rules = listOf(
            ruleFor(chrome, "*.example.com"),
            ruleFor(firefox, "*.example.com"),
        )
        val decision = engine.resolve(
            url = "https://foo.example.com",
            rules = rules,
            browsers = listOf(safari, chrome, firefox),
            exclusions = emptySet(),
        )
        val direct = assertIs<RuleDecision.Direct>(decision)
        assertEquals(chrome, direct.browser)
    }

    @Test
    fun missingBrowserContinuesSearchAndLogsInDebug() {
        val log = mutableListOf<String>()
        val engine = RuleEngine(debug = true, log = { log += it })
        val rules = listOf(
            ruleFor(firefox, "*.example.com"), // firefox not installed below
            ruleFor(chrome, "*.example.com"),
        )
        val decision = engine.resolve(
            url = "https://foo.example.com",
            rules = rules,
            browsers = listOf(safari, chrome), // no firefox
            exclusions = emptySet(),
        )
        val direct = assertIs<RuleDecision.Direct>(decision)
        assertEquals(chrome, direct.browser)
        assertTrue(log.any { it.contains("not installed") }, "expected a 'not installed' debug log, got: $log")
    }

    @Test
    fun excludedBrowserContinuesSearchAndLogsInDebug() {
        val log = mutableListOf<String>()
        val engine = RuleEngine(debug = true, log = { log += it })
        val rules = listOf(
            ruleFor(firefox, "*.example.com"), // firefox installed but excluded
            ruleFor(chrome, "*.example.com"),
        )
        val decision = engine.resolve(
            url = "https://foo.example.com",
            rules = rules,
            browsers = listOf(safari, chrome, firefox),
            exclusions = setOf(firefox.toBrowserId()),
        )
        val direct = assertIs<RuleDecision.Direct>(decision)
        assertEquals(chrome, direct.browser)
        assertTrue(log.any { it.contains("excluded") }, "expected an 'excluded' debug log, got: $log")
    }

    @Test
    fun pickerWhenAllMatchingRulesAreSkipped() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://foo.example.com",
            rules = listOf(ruleFor(firefox, "*.example.com")),
            browsers = listOf(safari, chrome), // firefox not present
            exclusions = emptySet(),
        )
        assertEquals(RuleDecision.Picker, decision)
    }

    @Test
    fun pickerForUrlWithoutHost() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "mailto:foo@example.com",
            rules = listOf(ruleFor(firefox, "*.example.com")),
            browsers = listOf(safari, firefox),
            exclusions = emptySet(),
        )
        assertEquals(RuleDecision.Picker, decision)
    }

    @Test
    fun pickerForMalformedUrl() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "not a url",
            rules = listOf(ruleFor(firefox, "*")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        assertEquals(RuleDecision.Picker, decision)
    }

    @Test
    fun stripsUserInfoAndPortFromHost() {
        val engine = RuleEngine()
        // Host should be `example.com`, not `user:pass@example.com:8080`.
        val decision = engine.resolve(
            url = "https://user:pass@example.com:8080/path?q=1",
            rules = listOf(ruleFor(firefox, "example.com")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        val direct = assertIs<RuleDecision.Direct>(decision)
        assertEquals(firefox, direct.browser)
    }

    @Test
    fun caseInsensitiveHostMatching() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "HTTPS://Example.COM/foo",
            rules = listOf(ruleFor(firefox, "example.com")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        assertIs<RuleDecision.Direct>(decision)
    }

    @Test
    fun ipv6LiteralHostIsExtractedWithBrackets() {
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://[::1]:8080/path",
            // Pattern matches the bracketed literal — that's what the parser yields.
            rules = listOf(ruleFor(firefox, "[::1]")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        assertIs<RuleDecision.Direct>(decision)
    }

    @Test
    fun ipv6LiteralWithoutClosingBracketStillParsesAsHost() {
        // Defensive — malformed IPv6 with no `]` should still produce a host
        // (everything after the scheme), not crash.
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://[::1/path",
            rules = listOf(ruleFor(firefox, "*")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        // `*` is greedy enough to match anything, including the malformed host.
        assertIs<RuleDecision.Direct>(decision)
    }

    @Test
    fun urlWithEmptyHostFallsThroughToPicker() {
        val engine = RuleEngine()
        // `https:///foo` — scheme + immediately path, no host.
        val decision = engine.resolve(
            url = "https:///foo",
            rules = listOf(ruleFor(firefox, "*")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        assertEquals(RuleDecision.Picker, decision)
    }

    @Test
    fun urlWithFragmentOnlyAfterSchemeStripsCorrectly() {
        // No path/query, just a fragment. Host should still be parsed.
        val engine = RuleEngine()
        val decision = engine.resolve(
            url = "https://example.com#anchor",
            rules = listOf(ruleFor(firefox, "example.com")),
            browsers = listOf(firefox),
            exclusions = emptySet(),
        )
        assertIs<RuleDecision.Direct>(decision)
    }

    @Test
    fun debugFalseProducesNoLogs() {
        val log = mutableListOf<String>()
        val engine = RuleEngine(debug = false, log = { log += it })
        engine.resolve(
            url = "https://foo.example.com",
            rules = listOf(
                ruleFor(firefox, "*.example.com"),
                ruleFor(chrome, "*.example.com"),
            ),
            browsers = listOf(chrome), // firefox missing — would log if debug=true
            exclusions = emptySet(),
        )
        assertEquals(emptyList(), log)
    }
}
