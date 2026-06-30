package io.github.klppl.ruta.ui.home

import androidx.compose.ui.graphics.Color
import io.github.klppl.ruta.R
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.model.Services

/**
 * Bundled glyph + brand colour for a service, if one applies. Matches a built-in directly by
 * [AppService.id], and — so a user-*added* site at a known brand's host (e.g. a custom "x.com"
 * or "tiktok.com", whose id is the host, not "x"/"tiktok") still gets the crisp on-brand tile
 * instead of a fetched favicon — falls back to matching by host via [Services.forHost].
 */
fun brandTileFor(service: AppService): Pair<Int, Color>? {
    brandIconRes(service.id)?.let { return it to service.brandColor }
    val match = Services.forHost(service.host) ?: return null
    return brandIconRes(match.id)?.let { it to match.brandColor }
}

/** Bundled brand glyphs (Simple Icons, CC0). Returns null for ids without a bundled logo. */
fun brandIconRes(id: String): Int? = when (id) {
    "x" -> R.drawable.ic_brand_x
    "instagram" -> R.drawable.ic_brand_instagram
    "facebook" -> R.drawable.ic_brand_facebook
    "tiktok" -> R.drawable.ic_brand_tiktok
    "reddit" -> R.drawable.ic_brand_reddit
    "bluesky" -> R.drawable.ic_brand_bluesky
    "threads" -> R.drawable.ic_brand_threads
    "linkedin" -> R.drawable.ic_brand_linkedin
    "tumblr" -> R.drawable.ic_brand_tumblr
    "mastodon" -> R.drawable.ic_brand_mastodon
    else -> null
}
