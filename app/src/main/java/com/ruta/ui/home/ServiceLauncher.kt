package com.ruta.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.ruta.model.AppService
import com.ruta.model.Services
import com.ruta.profile.Profile
import com.ruta.ui.components.ProfileChips

@Composable
fun ServiceLauncher(
    profiles: List<Profile>,
    selectedProfileId: String,
    onSelectProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onOpenService: (AppService) -> Unit,
    onSearch: () -> Unit,
    customServices: List<AppService>,
    onAddCustom: () -> Unit,
    onRemoveCustom: (AppService) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(Services.all, key = { it.id }) { service ->
                ServiceTile(service = service, onClick = { onOpenService(service) }, onLongClick = null)
            }
            items(customServices, key = { it.id }) { service ->
                ServiceTile(
                    service = service,
                    onClick = { onOpenService(service) },
                    onLongClick = { onRemoveCustom(service) },
                )
            }
            item(key = "__add_custom") { AddTile(onClick = onAddCustom) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceTile(service: AppService, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val background = if (service.isCustom) Color.White else service.brandColor
    val onColor = if (background.luminance() < 0.5f) Color.White else Color(0xFF1C1B1F)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            val iconRes = brandIconRes(service.id)
            when {
                service.isCustom && service.faviconUrl != null -> Favicon(service, onColor)
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = service.name,
                    tint = onColor,
                    modifier = Modifier.size(34.dp),
                )
                else -> Monogram(service.monogram, onColor)
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
private fun Favicon(service: AppService, fallbackColor: Color) {
    SubcomposeAsyncImage(
        model = service.faviconUrl,
        contentDescription = service.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (painter.state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Monogram(service.monogram, fallbackColor)
            }
        }
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
                contentDescription = "Add custom site",
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
