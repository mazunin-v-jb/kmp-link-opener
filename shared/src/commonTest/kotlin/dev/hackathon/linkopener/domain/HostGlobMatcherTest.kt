package dev.hackathon.linkopener.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostGlobMatcherTest {

    @Test
    fun starCoversAnySingleSubdomain() {
        assertTrue(HostGlobMatcher.matches("*.example.com", "foo.example.com"))
    }

    @Test
    fun starCoversNestedSubdomains() {
        // The chosen semantics: `*` matches any characters including dots,
        // so deeply nested hosts also match.
        assertTrue(HostGlobMatcher.matches("*.example.com", "bar.baz.qux.example.com"))
    }

    @Test
    fun exactPatternMatchesItself() {
        assertTrue(HostGlobMatcher.matches("youtube.com", "youtube.com"))
    }

    @Test
    fun exactPatternDoesNotMatchSubdomain() {
        assertFalse(HostGlobMatcher.matches("youtube.com", "www.youtube.com"))
    }

    @Test
    fun starOnBothSidesMatchesNestedDomains() {
        assertTrue(HostGlobMatcher.matches("*.work.*", "email.work.com"))
        assertTrue(HostGlobMatcher.matches("*.work.*", "git.work.io"))
        assertTrue(HostGlobMatcher.matches("*.work.*", "email.sub.work.io.test"))
    }

    @Test
    fun starOnBothSidesAlsoCoversBareDomain() {
        // Under the cookie-domain shortcut for leading `*.`, the bare
        // `work.com` is included by `*.work.*` (the leading `*.` is
        // optional, the trailing `*` matches `com`). Same logic as
        // `*.vk.com` covering `vk.com`.
        assertTrue(HostGlobMatcher.matches("*.work.*", "work.com"))
    }

    @Test
    fun caseInsensitive() {
        assertTrue(HostGlobMatcher.matches("*.YouTube.com", "Foo.youtube.com"))
        assertTrue(HostGlobMatcher.matches("YOUTUBE.COM", "youtube.com"))
    }

    @Test
    fun patternWithRegexMetacharactersIsLiteral() {
        // `.` should match a literal dot, not "any char". So `1.0.0` matches
        // `1.0.0` but not `1x0x0` — confirms metachars are escaped.
        assertTrue(HostGlobMatcher.matches("1.0.0", "1.0.0"))
        assertFalse(HostGlobMatcher.matches("1.0.0", "1x0x0"))
    }

    @Test
    fun doubleStarBehavesLikeSingle() {
        // Should not blow up; double-star is just `.*.*` which still matches
        // anything matched by `*`.
        assertTrue(HostGlobMatcher.matches("**.example.com", "foo.example.com"))
    }

    @Test
    fun emptyPatternNeverMatches() {
        assertFalse(HostGlobMatcher.matches("", "example.com"))
        assertFalse(HostGlobMatcher.matches("   ", "example.com"))
    }

    @Test
    fun emptyHostNeverMatches() {
        assertFalse(HostGlobMatcher.matches("*.example.com", ""))
        assertFalse(HostGlobMatcher.matches("anything", "   "))
    }

    @Test
    fun pureStarMatchesAnything() {
        assertTrue(HostGlobMatcher.matches("*", "anything.com"))
        assertTrue(HostGlobMatcher.matches("*", "x"))
    }

    @Test
    fun leadingStarDotAlsoMatchesBareDomain() {
        // Cookie-domain shortcut: `*.foo.bar` is meant to cover the parent
        // domain too, not just subdomains. So `*.vk.com` matches `vk.com`,
        // and `*.example.com` matches `example.com`.
        assertTrue(HostGlobMatcher.matches("*.vk.com", "vk.com"))
        assertTrue(HostGlobMatcher.matches("*.example.com", "example.com"))
        // Sub- and sub-sub-domains still match.
        assertTrue(HostGlobMatcher.matches("*.vk.com", "m.vk.com"))
        assertTrue(HostGlobMatcher.matches("*.vk.com", "oauth.m.vk.com"))
        // Different domain still doesn't.
        assertFalse(HostGlobMatcher.matches("*.vk.com", "example.com"))
    }
}
