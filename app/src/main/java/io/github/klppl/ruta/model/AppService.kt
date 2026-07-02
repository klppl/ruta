package io.github.klppl.ruta.model

import androidx.compose.ui.graphics.Color

/**
 * A wrapped social network. [host] is the canonical apex domain used for content-script
 * module selection and per-site settings; [url] is the landing page.
 */
data class AppService(
    val id: String,
    val name: String,
    val url: String,
    val host: String,
    val brandColor: Color,
    /** Single-letter monogram used by the launcher tile when no icon font is bundled. */
    val monogram: String = name.take(1),
    /** True for user-added bookmarks (rendered with a favicon and removable). */
    val isCustom: Boolean = false,
    /**
     * Other apex domains the service also lives on (threads.com for threads.net, twitter.com
     * for x.com). [host] stays the canonical id — it keys per-site profiles, so it must never
     * change for an existing service — but host matching honours these too.
     */
    val aliasHosts: Set<String> = emptySet(),
) {
    /** True if [candidate] (a lowercase hostname) is this service's host, an alias, or a subdomain of either. */
    fun matchesHost(candidate: String): Boolean =
        (aliasHosts + host).any { candidate == it || candidate.endsWith(".$it") }
}

object Services {
    val all: List<AppService> = listOf(
        AppService("x", "X", "https://x.com", "x.com", Color(0xFF000000), "𝕏", aliasHosts = setOf("twitter.com")),
        AppService("instagram", "Instagram", "https://www.instagram.com", "instagram.com", Color(0xFFE1306C), "Ig"),
        AppService("facebook", "Facebook", "https://www.facebook.com", "facebook.com", Color(0xFF1877F2), "f"),
        AppService("tiktok", "TikTok", "https://www.tiktok.com", "tiktok.com", Color(0xFF010101), "♪"),
        AppService("reddit", "Reddit", "https://www.reddit.com", "reddit.com", Color(0xFFFF4500), "r"),
        AppService("bluesky", "Bluesky", "https://bsky.app", "bsky.app", Color(0xFF1185FE), "B"),
        // Threads moved to threads.com (threads.net now 301s there); the url skips the redirect,
        // while host stays threads.net so existing per-site profiles keep their logins.
        AppService("threads", "Threads", "https://www.threads.com", "threads.net", Color(0xFF000000), "@", aliasHosts = setOf("threads.com")),
        AppService("linkedin", "LinkedIn", "https://www.linkedin.com", "linkedin.com", Color(0xFF0A66C2), "in"),
        AppService("tumblr", "Tumblr", "https://www.tumblr.com", "tumblr.com", Color(0xFF36465D), "t"),
        AppService("mastodon", "Mastodon", "https://mastodon.social", "mastodon.social", Color(0xFF6364FF), "m"),
    )

    fun forHost(host: String?): AppService? {
        if (host.isNullOrBlank()) return null
        val h = host.lowercase()
        return all.firstOrNull { it.matchesHost(h) }
    }
}
