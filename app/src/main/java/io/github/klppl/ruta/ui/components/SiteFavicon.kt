package io.github.klppl.ruta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

/**
 * Icon for a site, resolved at progressively lower fidelity so user-added sites still look
 * crisp instead of an upscaled 16px favicon:
 *
 *  1. the site's own `apple-touch-icon.png` (first-party, typically 120–180px),
 *  2. DuckDuckGo's icon service (small but reliable),
 *  3. [fallback] (a monogram or globe) when neither resolves.
 *
 * Each source is tried in turn: a load error advances to the next. Rendered with
 * [ContentScale.Fit] (not Crop) so a square app icon is letterboxed, never stretched.
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
            "https://icons.duckduckgo.com/ip3/$host.ico",
        )
    }
    var index by remember(host) { mutableIntStateOf(0) }
    if (index > sources.lastIndex) {
        fallback()
        return
    }
    SubcomposeAsyncImage(
        model = sources[index],
        contentDescription = contentDescription,
        contentScale = contentScale,
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
