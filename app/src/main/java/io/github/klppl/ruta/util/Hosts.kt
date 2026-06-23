package io.github.klppl.ruta.util

import android.net.Uri

object Hosts {
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
    }

    /**
     * Approximate registrable domain (eTLD+1) — the last two labels, e.g.
     * "m.facebook.com" -> "facebook.com". Good enough to tell "same site" from "external"
     * for the social hosts we care about (no public-suffix list, so co.uk etc. are coarse).
     */
    fun registrable(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val labels = host.split('.').filter { it.isNotEmpty() }
        return if (labels.size <= 2) host else labels.takeLast(2).joinToString(".")
    }

    /**
     * Yields a host and each of its parent domains, longest first.
     * e.g. "a.b.example.com" -> a.b.example.com, b.example.com, example.com, com
     */
    inline fun forEachSuffix(host: String, action: (String) -> Boolean): Boolean {
        var start = 0
        val h = host
        // First the full host.
        if (action(h)) return true
        while (true) {
            val dot = h.indexOf('.', start)
            if (dot < 0) break
            start = dot + 1
            if (start >= h.length) break
            if (action(h.substring(start))) return true
        }
        return false
    }
}
