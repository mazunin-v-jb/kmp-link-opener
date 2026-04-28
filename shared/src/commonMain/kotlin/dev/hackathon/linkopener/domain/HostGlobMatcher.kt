package dev.hackathon.linkopener.domain

/**
 * Glob matcher for the host part of a URL. Single source of truth for the
 * pattern semantics of [dev.hackathon.linkopener.core.model.UrlRule]
 * — change the body here to flip decision #1 (host-glob → regex / full-URL
 * wildcard / etc.) without touching anything else in the graph.
 *
 * Current semantics:
 * - `*` matches any sequence of characters **including dots**, so
 *   `*.example.com` covers `foo.example.com` and `bar.baz.example.com`.
 * - A leading `*.` is special: it also matches the **bare domain** itself.
 *   That is, `*.example.com` covers `example.com` too. This is the
 *   cookie-domain convention (`.example.com` cookies apply to the bare
 *   domain plus all subdomains) — it matches what users typing
 *   `*.vk.com` actually intend.
 * - All other characters are matched literally (regex metacharacters in the
 *   pattern are escaped — `1.0.0` does not match `1x0x0`).
 * - Match is case-insensitive (hostnames are registry-defined as ASCII
 *   case-insensitive).
 * - Empty pattern never matches.
 */
object HostGlobMatcher {

    fun matches(pattern: String, host: String): Boolean {
        val cleanedPattern = pattern.trim()
        val cleanedHost = host.trim()
        if (cleanedPattern.isEmpty() || cleanedHost.isEmpty()) return false
        val regex = compile(cleanedPattern)
        return regex.matches(cleanedHost.lowercase())
    }

    private fun compile(pattern: String): Regex {
        // Build the regex piece by piece: keep `*` as `.*`, escape every
        // other character literally. Avoids surprises from patterns like
        // `1.0.0` or `*.example.com?` looking like regex.
        val sb = StringBuilder()
        val lowered = pattern.lowercase()
        var startIndex = 0
        if (lowered.startsWith("*.")) {
            // Cookie-domain shortcut: leading `*.` is an optional subdomain
            // prefix that may also be empty, so `*.vk.com` matches the bare
            // `vk.com` in addition to any `<sub>.vk.com`. Without this, the
            // strict glob form requires at least one char + dot.
            sb.append("(.*\\.)?")
            startIndex = 2
        }
        for (i in startIndex until lowered.length) {
            val ch = lowered[i]
            if (ch == '*') {
                sb.append(".*")
            } else {
                sb.append(Regex.escape(ch.toString()))
            }
        }
        return Regex(sb.toString())
    }
}
