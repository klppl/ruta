package com.ruta.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import com.ruta.model.Services
import com.ruta.model.Tab
import com.ruta.ui.theme.accentForProfile
import com.ruta.util.Hosts

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
                onClick = { onSelect(tab.id) },
                onClose = { onClose(tab.id) },
            )
        }
        item(key = "__add") { AddButton(onNewTab) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(tab: Tab, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val service = Services.forHost(Hosts.hostOf(tab.url))
    val accent = accentForProfile(tab.profileId)
    val iconRes = service?.let { brandIconRes(it.id) }
    val ring = if (active) Modifier.border(2.5.dp, accent, CircleShape)
    else Modifier.border(1.dp, accent.copy(alpha = 0.4f), CircleShape)

    Surface(
        modifier = Modifier
            .size(46.dp)
            .alpha(if (active) 1f else 0.55f),
        shape = CircleShape,
        color = service?.brandColor ?: MaterialTheme.colorScheme.surface,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(CircleShape)
                .then(ring)
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
