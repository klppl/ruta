package io.github.klppl.ruta.ui.tabswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.ui.theme.accentForProfile
import io.github.klppl.ruta.util.Hosts

@Composable
fun TabSwitcher(
    tabs: List<Tab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTab,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New tab") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            items(tabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    active = tab.id == activeId,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                )
            }
        }
    }
}

@Composable
private fun TabCard(tab: Tab, active: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val accent = accentForProfile(tab.profileId)
    val host = Hosts.hostOf(tab.url) ?: "New tab"
    val borderMod = if (active) Modifier.border(2.dp, accent, RoundedCornerShape(20.dp)) else Modifier
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(borderMod)
                .clickable(onClick = onSelect),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                text = tab.title.ifBlank { host },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = host,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}
