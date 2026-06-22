package io.github.klppl.ruta.blocking

/**
 * Clean-room parser for a useful subset of Adblock Plus filter syntax plus hostfile lines.
 *
 * It deliberately implements only what this app can enforce:
 *  - Network: host-anchored `||host^` block rules, `@@||host^` allow rules, and
 *    `0.0.0.0 host` / `127.0.0.1 host` hostfile entries. Path/regex/wildcard network
 *    rules are dropped (they can't be matched allocation-free in shouldInterceptRequest).
 *  - Cosmetic: `##` (hide) and `#@#` (unhide) element-hiding rules with their optional
 *    `domain.com##selector` restriction. Procedural / scriptlet rules are dropped.
 *
 * Parse multiple lists into one [Accumulator], then [Accumulator.build].
 */
object AbpParser {

    /** Selector fragments we cannot evaluate with plain CSS — drop the whole rule. */
    private val proceduralTokens = listOf(
        ":has-text(", "+js(", ":xpath(", ":style(", ":-abp-",
        ":matches-css", ":upward(", ":remove(", ":contains(",
    )

    class Accumulator {
        val blocked = HashSet<String>(1 shl 16)
        val allowed = HashSet<String>(1 shl 12)
        val genericHide = HashSet<String>(1 shl 14)
        val genericUnhide = HashSet<String>()
        val domainHide = HashMap<String, MutableSet<String>>()
        val domainUnhide = HashMap<String, MutableSet<String>>()

        fun build(): ParseResult = ParseResult(
            network = NetworkRules(blocked, allowed),
            cosmetic = CosmeticRules(
                genericHide = genericHide,
                genericUnhide = genericUnhide,
                domainHide = domainHide.mapValues { it.value },
                domainUnhide = domainUnhide.mapValues { it.value },
            ),
        )
    }

    fun parseInto(text: String, acc: Accumulator) {
        var i = 0
        val n = text.length
        while (i < n) {
            var end = text.indexOf('\n', i)
            if (end < 0) end = n
            parseLine(text, i, end, acc)
            i = end + 1
        }
    }

    private fun parseLine(text: String, start: Int, end: Int, acc: Accumulator) {
        // Trim leading/trailing whitespace and CR.
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (s >= e) return

        val line = text.substring(s, e)
        val c0 = line[0]
        if (c0 == '!' || c0 == '[') return // comment / metadata section

        // Hostfile entries: "0.0.0.0 host" or "127.0.0.1 host".
        if (line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")) {
            val sp = line.indexOfFirst { it == ' ' || it == '\t' }
            if (sp > 0) {
                val rest = line.substring(sp + 1).trim()
                val host = rest.substringBefore('#').trim().substringBefore(' ')
                if (isValidHost(host) && host != "localhost") acc.blocked.add(host.lowercase())
            }
            return
        }

        // Cosmetic rules.
        line.indexOf("#@#").let { idx ->
            if (idx >= 0) { parseCosmetic(line, idx, 3, unhide = true, acc); return }
        }
        if (line.contains("#?#") || line.contains("#$#") || line.contains("#%#")) return // procedural/snippet
        line.indexOf("##").let { idx ->
            if (idx >= 0) { parseCosmetic(line, idx, 2, unhide = false, acc); return }
        }

        // Network rules.
        var rule = line
        var allow = false
        if (rule.startsWith("@@")) { allow = true; rule = rule.substring(2) }
        if (!rule.startsWith("||")) return // only host-anchored rules are enforced
        val host = extractHost(rule, 2) ?: return
        if (allow) acc.allowed.add(host) else acc.blocked.add(host)
    }

    private fun parseCosmetic(
        line: String,
        sepIndex: Int,
        sepLen: Int,
        unhide: Boolean,
        acc: Accumulator,
    ) {
        val selector = line.substring(sepIndex + sepLen).trim()
        if (selector.isEmpty()) return
        if (proceduralTokens.any { selector.contains(it) }) return

        val domainsPart = line.substring(0, sepIndex)
        val positives = ArrayList<String>()
        val negatives = ArrayList<String>()
        if (domainsPart.isNotEmpty()) {
            for (raw in domainsPart.split(',')) {
                val d = raw.trim().lowercase()
                if (d.isEmpty()) continue
                if (d.startsWith("~")) negatives.add(d.substring(1)) else positives.add(d)
            }
        }

        if (unhide) {
            if (positives.isEmpty()) acc.genericUnhide.add(selector)
            else for (d in positives) acc.domainUnhide.getOrPut(d) { HashSet() }.add(selector)
        } else {
            if (positives.isEmpty()) {
                acc.genericHide.add(selector)
                for (d in negatives) acc.domainUnhide.getOrPut(d) { HashSet() }.add(selector)
            } else {
                for (d in positives) acc.domainHide.getOrPut(d) { HashSet() }.add(selector)
            }
        }
    }

    /** Extract a pure host from a `||`-anchored rule; null if the rule is path/wildcard. */
    private fun extractHost(rule: String, from: Int): String? {
        var i = from
        val n = rule.length
        while (i < n) {
            when (val ch = rule[i]) {
                '^', '|', '$' -> return finishHost(rule, from, i)
                '/', '*', '?', '=', '@' -> return null
                else -> if (!(ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_')) return null
            }
            i++
        }
        return finishHost(rule, from, n)
    }

    private fun finishHost(rule: String, from: Int, to: Int): String? {
        if (to <= from) return null
        val host = rule.substring(from, to).lowercase()
        return if (isValidHost(host)) host else null
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return false
        for (ch in host) {
            if (!(ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_')) return false
        }
        return true
    }
}
