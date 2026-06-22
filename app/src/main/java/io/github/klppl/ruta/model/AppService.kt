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
    /** Remote favicon used for user-added custom sites (null for built-ins, which use bundled glyphs). */
    val faviconUrl: String? = null,
    /** True for user-added bookmarks (rendered with a favicon and removable). */
    val isCustom: Boolean = false,
)

object Services {
    val all: List<AppService> = listOf(
        AppService("x", "X", "https://x.com", "x.com", Color(0xFF000000), "𝕏"),
        AppService("instagram", "Instagram", "https://www.instagram.com", "instagram.com", Color(0xFFE1306C), "Ig"),
        AppService("facebook", "Facebook", "https://www.facebook.com", "facebook.com", Color(0xFF1877F2), "f"),
        AppService("tiktok", "TikTok", "https://www.tiktok.com", "tiktok.com", Color(0xFF010101), "♪"),
        AppService("reddit", "Reddit", "https://www.reddit.com", "reddit.com", Color(0xFFFF4500), "r"),
        AppService("bluesky", "Bluesky", "https://bsky.app", "bsky.app", Color(0xFF1185FE), "B"),
        AppService("threads", "Threads", "https://www.threads.net", "threads.net", Color(0xFF000000), "@"),
        AppService("linkedin", "LinkedIn", "https://www.linkedin.com", "linkedin.com", Color(0xFF0A66C2), "in"),
        AppService("tumblr", "Tumblr", "https://www.tumblr.com", "tumblr.com", Color(0xFF36465D), "t"),
        AppService("mastodon", "Mastodon", "https://mastodon.social", "mastodon.social", Color(0xFF6364FF), "m"),
    )

    fun forHost(host: String?): AppService? {
        if (host.isNullOrBlank()) return null
        val h = host.lowercase()
        return all.firstOrNull { h == it.host || h.endsWith("." + it.host) }
    }
}
