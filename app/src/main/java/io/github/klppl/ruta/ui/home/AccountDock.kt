package io.github.klppl.ruta.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import io.github.klppl.ruta.model.Services
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.ui.theme.accentForProfile
import io.github.klppl.ruta.util.Hosts

/**
 * A thumb-reachable horizontal switcher for open accounts/sites. Each item is a brand glyph
 * ringed in its profile accent; the active tab is bright with a thicker ring. Tap to switch,
 * long-press to close, trailing + opens a new tab.
 */
@Composable
fun AccountDock(
    tabs: List<Tab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onReset: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.id }) { tab ->
            DockItem(
                tab = tab,
                active = tab.id == activeId,
                // Tapping the active account again resets it to the service's home URL.
                onClick = { if (tab.id == activeId) onReset(tab.id) else onSelect(tab.id) },
                onClose = { onClose(tab.id) },
            )
        }
        item(key = "__add") { AddButton(onNewTab) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(tab: Tab, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val host = Hosts.hostOf(tab.url)
    val service = Services.forHost(host)
    val accent = accentForProfile(tab.profileId)
    val iconRes = service?.let { brandIconRes(it.id) }
    val faviconUrl = if (service == null && host != null) "https://icons.duckduckgo.com/ip3/$host.ico" else null

    Surface(
        modifier = Modifier
            .size(46.dp)
            .alpha(if (active) 1f else 0.55f),
        shape = CircleShape,
        color = service?.brandColor ?: if (faviconUrl != null) Color.White else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (active) 2.5.dp else 1.dp,
            color = if (active) accent else accent.copy(alpha = 0.4f),
        ),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(onClick = onClick, onLongClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            when {
                iconRes != null -> Icon(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = service?.name,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                faviconUrl != null -> SubcomposeAsyncImage(
                    model = faviconUrl,
                    contentDescription = host,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (painter.state is AsyncImagePainter.State.Success) {
                        SubcomposeAsyncImageContent()
                    } else {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "Site",
                                tint = Color(0xFF1C1B1F),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                else -> Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = "Site",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
