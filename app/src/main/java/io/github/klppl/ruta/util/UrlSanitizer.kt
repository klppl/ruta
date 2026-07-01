package io.github.klppl.ruta.util

import android.net.Uri

/**
 * Removes tracking query parameters from URLs. Used both by the clipboard cleaner and
 * exposed to the page script via the bridge so `navigator.clipboard.writeText` is wrapped.
 */
object UrlSanitizer {

    /** Always stripped, regardless of host. */
    private val globalParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "mc_eid", "mc_cid",
        "igsh", "igshid", "xmt", "ref_src", "ref_url",
        "share_id", "rdt", "correlation_id",
    )

    /** Param name prefixes that are always stripped. */
    private val globalPrefixes = listOf("utm_", "_branch", "yclid")

    /** Host-specific params keyed by apex domain suffix. */
    private val hostParams = mapOf(
        "twitter.com" to setOf("s", "t", "ref_src"),
        "x.com" to setOf("s", "t", "ref_src"),
        "instagram.com" to setOf("igsh", "igshid", "img_index"),
        "tiktok.com" to setOf("is_from_webapp", "sender_device", "web_id", "_r", "_t"),
        "reddit.com" to setOf("ref", "ref_source", "share_id", "rdt", "correlation_id"),
        // "si" is a share-tracking param only on YouTube/Spotify — stripping it globally
        // would break legitimate `si` params elsewhere.
        "youtube.com" to setOf("feature", "pp", "si"),
        "youtu.be" to setOf("feature", "pp", "si"),
        "spotify.com" to setOf("si"),
        "facebook.com" to setOf("mibextid", "rdid", "comment_id"),
    )

    fun isHttp(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    fun sanitize(url: String): String {
        if (!isHttp(url)) return url
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        if (uri.isOpaque || uri.query == null) return url

        val host = uri.host?.lowercase().orEmpty()
        val extra = hostParams.entries
            .firstOrNull { host == it.key || host.endsWith("." + it.key) }
            ?.value.orEmpty()

        val kept = uri.queryParameterNames.filterNot { name ->
            val lower = name.lowercase()
            lower in globalParams ||
                lower in extra ||
                globalPrefixes.any { lower.startsWith(it) }
        }

        if (kept.size == uri.queryParameterNames.size) return url

        val builder = uri.buildUpon().clearQuery()
        for (name in kept) {
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        return builder.build().toString()
    }
}
