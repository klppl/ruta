package io.github.klppl.ruta.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.klppl.ruta.blocking.BlockStats
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.model.Services
import io.github.klppl.ruta.profile.Profile
import io.github.klppl.ruta.ui.components.ProfileChips
import io.github.klppl.ruta.ui.components.SiteFavicon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
fun ServiceLauncher(
    profiles: List<Profile>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onOpenService: (AppService) -> Unit,
    onSearch: () -> Unit,
    dashboardServices: List<AppService>,
    stats: BlockStats,
    rulesLoaded: Int,
    editMode: Boolean,
    onEnterEdit: () -> Unit,
    onExitEdit: () -> Unit,
    onCommitOrder: (List<String>) -> Unit,
    onRemoveService: (AppService) -> Unit,
    onEditService: (AppService) -> Unit,
    onAddCustom: () -> Unit,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Once the user has any of their own sites the launcher shows just those (decluttered), with
    // the rest of the catalog behind "+". Before that — a fresh install — it shows the built-ins
    // as suggestions so there's something to tap, and "+" adds a custom URL.
    val hasSites = dashboardServices.isNotEmpty()
    val onAddTile = if (hasSites) onOpenCatalog else onAddCustom

    // Local copy so drags rearrange instantly; the order is persisted once, on drag end
    // (per-move persistence would race the DataStore round-trip mid-drag).
    var tiles by remember(dashboardServices) {
        mutableStateOf(if (hasSites) dashboardServices else Services.all)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            text = "ruta",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
        )
        Text(
            text = "A calmer way into your feeds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Surface(
            onClick = onSearch,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Search or enter address",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        ProfileChips(
            profiles = profiles,
            selectedId = selectedProfileId,
            onSelect = onSelectProfile,
            onAddProfile = onAddProfile,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (stats.total > 0) {
            DashboardStats(
                stats = stats,
                sites = dashboardServices.size,
                rulesLoaded = rulesLoaded,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Text(
                text = when {
                    editMode -> "Drag to arrange"
                    hasSites -> "Your sites"
                    else -> "Suggested"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (editMode) {
                TextButton(onClick = onExitEdit) { Text("Done") }
            }
        }
        val gridState = rememberLazyGridState()
        val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
            tiles = tiles.toMutableList().apply {
                if (from.index in indices && to.index in indices) add(to.index, removeAt(from.index))
            }
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(tiles, key = { it.id }) { service ->
                ReorderableItem(reorderState, key = service.id) { dragging ->
                    val dragHandle = if (editMode) {
                        Modifier.draggableHandle(
                            onDragStopped = { onCommitOrder(tiles.map { it.id }) },
                        )
                    } else {
                        Modifier
                    }
                    ServiceTile(
                        service = service,
                        onClick = { if (editMode) onExitEdit() else onOpenService(service) },
                        onLongClick = if (hasSites && !editMode) onEnterEdit else null,
                        editMode = editMode,
                        dragging = dragging,
                        dragHandle = dragHandle,
                        onRemove = { onRemoveService(service) },
                        onEdit = if (service.isCustom) ({ onEditService(service) }) else null,
                    )
                }
            }
            if (!editMode) {
                item(key = "__add") { AddTile(onClick = onAddTile) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ServiceTile(
    service: AppService,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    dragging: Boolean = false,
    dragHandle: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    val brand = brandTileFor(service)
    val background = brand?.second ?: if (service.isCustom) Color.White else service.brandColor
    val onColor = if (background.luminance() < 0.5f) Color.White else Color(0xFF1C1B1F)
    val interaction = if (editMode) {
        dragHandle.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(if (dragging) 1.08f else 1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(background)
                    .then(interaction),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    brand != null -> Icon(
                        painter = painterResource(brand.first),
                        contentDescription = service.name,
                        tint = onColor,
                        modifier = Modifier.size(34.dp),
                    )
                    // No bundled glyph (a custom site at an unknown host) -> the site's own icon,
                    // centered and letterboxed rather than stretched across the whole tile.
                    service.host.isNotBlank() -> SiteFavicon(
                        host = service.host,
                        contentDescription = service.name,
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        fallback = { Monogram(service.monogram, onColor) },
                    )
                    else -> Monogram(service.monogram, onColor)
                }
            }
            if (editMode && onRemove != null) {
                TileBadge(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Remove ${service.name}",
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp),
                )
            }
            if (editMode && onEdit != null) {
                TileBadge(
                    icon = Icons.Rounded.Edit,
                    contentDescription = "Edit ${service.name}",
                    onClick = onEdit,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = (-5).dp, y = (-5).dp),
                )
            }
        }
        Text(
            text = service.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TileBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun Monogram(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "Add site",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
