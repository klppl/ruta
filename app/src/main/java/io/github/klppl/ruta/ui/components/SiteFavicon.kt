package io.github.klppl.ruta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

/**
 * Icon for a site, resolved from highest to lowest fidelity so user-added sites look crisp
 * instead of an upscaled 16px favicon:
 *
 *  1. the site's own `apple-touch-icon.png` (first-party, no third party sees the host; ~180px),
 *  2. `apple-touch-icon-precomposed.png` (older first-party variant some sites use instead),
 *  3. icon.horse — a high-res icon aggregator that returns 256px art for most sites, including
 *     SPAs like instagram.com / x.com whose own `/apple-touch-icon.png` answers with an HTML
 *     shell, and 404-only hosts like bsky.app / linkedin.com / mastodon.social,
 *  4. Google's favicon service at `sz=256`,
 *  5. DuckDuckGo's icon service (small but reliable),
 *  6. [fallback] (a monogram or globe) when none resolves.
 *
 * Each source is tried in turn: a load error (404, non-image, or undecodable body) advances to
 * the next. Sources 1–2 are first-party (privacy-preserving) and preferred; 3–5 leak the host to
 * a third party and are only reached when the site exposes no first-party icon. Every source is
 * decoded at 256px with a high-quality filter so the result stays sharp when scaled into the
 * tile, and rendered with [ContentScale.Fit] (not Crop) so a square app icon is letterboxed,
 * never stretched or clipped.
 */
@Composable
fun SiteFavicon(
    host: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit,
) {
    val sources = remember(host) {
        listOf(
            "https://$host/apple-touch-icon.png",
            "https://$host/apple-touch-icon-precomposed.png",
            "https://icon.horse/icon/$host",
            "https://www.google.com/s2/favicons?domain=$host&sz=256",
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )
    }
    var index by remember(host) { mutableIntStateOf(0) }
    if (index > sources.lastIndex) {
        fallback()
        return
    }
    val context = LocalContext.current
    val request = remember(sources[index]) {
        ImageRequest.Builder(context)
            .data(sources[index])
            .size(ICON_PX)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        filterQuality = FilterQuality.High,
        modifier = modifier,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            is AsyncImagePainter.State.Error -> {
                // This source failed (404, non-image, or undecodable) — fall through to the next.
                LaunchedEffect(index) { index++ }
                fallback()
            }
            else -> fallback()
        }
    }
}

/** Decode target: high enough to stay sharp in the largest tile on a high-density screen. */
private const val ICON_PX = 256
