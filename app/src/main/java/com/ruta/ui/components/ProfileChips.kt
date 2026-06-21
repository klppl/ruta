package com.ruta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ruta.profile.Profile

@Composable
fun ProfileChips(
    profiles: List<Profile>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        profiles.forEach { profile ->
            FilterChip(
                selected = profile.id == selectedId,
                onClick = { onSelect(profile.id) },
                label = { Text(profile.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(profile.accent),
                    )
                },
            )
        }
        FilterChip(
            selected = false,
            onClick = onAddProfile,
            label = { Text("New") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = "Add profile") },
        )
    }
}
