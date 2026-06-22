package io.github.klppl.ruta.blocking

import io.github.klppl.ruta.util.Hosts
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable host sets consumed by [RequestBlocker]. */
class NetworkRules(val blocked: Set<String>, val allowed: Set<String>) {
    companion object {
        val EMPTY = NetworkRules(emptySet(), emptySet())
    }
}

/**
 * Fast, hot-path host matcher used in `shouldInterceptRequest`. Performs longest-suffix
 * matching: walking from the most specific label, the first set that matches wins, and at
 * equal specificity the allow set wins (checked first).
 */
@Singleton
class RequestBlocker @Inject constructor() {

    @Volatile var enabled: Boolean = true
    @Volatile private var rules: NetworkRules = NetworkRules.EMPTY

    val ruleCount: Int get() = rules.blocked.size

    fun update(newRules: NetworkRules) {
        rules = newRules
    }

    fun shouldBlock(host: String?): Boolean {
        if (!enabled || host == null) return false
        val r = rules
        if (r.blocked.isEmpty()) return false

        // Fast path: exact host membership (the common case).
        if (r.allowed.contains(host)) return false
        if (r.blocked.contains(host)) return true

        var result = false
        Hosts.forEachSuffix(host) { suffix ->
            when {
                r.allowed.contains(suffix) -> { result = false; true }
                r.blocked.contains(suffix) -> { result = true; true }
                else -> false
            }
        }
        return result
    }
}
