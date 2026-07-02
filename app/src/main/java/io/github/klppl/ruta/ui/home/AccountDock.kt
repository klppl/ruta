package io.github.klppl.ruta.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.klppl.ruta.ui.components.SiteFavicon
import io.github.klppl.ruta.model.Services
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.ui.theme.accentForProfile
import io.github.klppl.ruta.util.Hosts
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * A thumb-reachable horizontal switcher for open accounts/sites. Each item is a brand glyph (or
 * favicon) ringed in its profile accent; the active tab is bright with a thicker ring.
 *
 * Tap to switch (tap the active one again to reload its home page). Long-press any icon to enter
 * edit mode: icons can be dragged to reorder and each shows an ✕ to close that tab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountDock(
    tabs: List<Tab>,
    activeId: String?,
    editMode: Boolean,
    onSelect: (String) -> Unit,
    onReset: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(tabs, key = { it.id }) { tab ->
                ReorderableItem(reorderState, key = tab.id) { dragging ->
                    val dragHandle = if (editMode) Modifier.draggableHandle() else Modifier
                    DockItem(
                        tab = tab,
                        active = tab.id == activeId,
                        editMode = editMode,
                        dragging = dragging,
                        dragHandle = dragHandle,
                        onClick = {
                            when {
                                editMode -> { onExitEdit(); onSelect(tab.id) }
                                tab.id == activeId -> onReset(tab.id)
                                else -> onSelect(tab.id)
                            }
                        },
                        onLongClick = { if (!editMode) onEnterEdit() },
                        onClose = { onClose(tab.id) },
                    )
                }
            }
        }
        AddButton(onClick = { onExitEdit(); onNewTab() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    tab: Tab,
    active: Boolean,
    editMode: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClose: () -> Unit,
) {
    val host = Hosts.hostOf(tab.url)
    val service = Services.forHost(host)
    val accent = accentForProfile(tab.profileId)
    val iconRes = service?.let { brandIconRes(it.id) }
    // No bundled glyph (Play flavor, or a non-built-in site) -> the site's own icon.
    val showFavicon = iconRes == null && !host.isNullOrBlank()

    val interaction = if (editMode) {
        dragHandle.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    Box {
        Surface(
            modifier = Modifier
                .size(46.dp)
                .scale(if (dragging) 1.12f else 1f)
                .alpha(if (active || editMode) 1f else 0.55f),
            shape = CircleShape,
            color = service?.brandColor ?: if (showFavicon) Color.White else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (active) 2.5.dp else 1.dp,
                color = if (active) accent else accent.copy(alpha = 0.4f),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().then(interaction), contentAlignment = Alignment.Center) {
                when {
                    iconRes != null -> Icon(
                        painter = painterResource(iconRes),
                        contentDescription = service?.name,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    showFavicon -> SiteFavicon(
                        host = host!!,
                        contentDescription = host,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        fallback = {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "Site",
                                tint = Color(0xFF1C1B1F),
                                modifier = Modifier.size(22.dp),
                            )
                        },
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

        if (editMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp),
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
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "New tab",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
