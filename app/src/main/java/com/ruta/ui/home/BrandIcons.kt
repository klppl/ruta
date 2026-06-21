package com.ruta.ui.home

import com.ruta.R

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
