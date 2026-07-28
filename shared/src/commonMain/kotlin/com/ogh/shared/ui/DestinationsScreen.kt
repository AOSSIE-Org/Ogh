package com.ogh.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogh.shared.ui.theme.OghColors
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationType

/**
 * Destination management screen.
 *
 * Full CRUD: add, edit, delete, enable/disable RTMP destinations.
 */
@Composable
fun DestinationsScreen(
    destinations: List<Destination>,
    onToggleDestination: (String) -> Unit,
    onDeleteDestination: (String) -> Unit,
    onBack: () -> Unit,
    onAddDestination: () -> Unit,
    onEditDestination: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<Destination?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBack() }
                            .wrapContentSize(Alignment.Center),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Destinations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "+ Add",
                    style = MaterialTheme.typography.labelLarge,
                    color = OghColors.Violet,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAddDestination() }
                        .padding(horizontal = 12.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        },
    ) { innerPadding ->
        if (destinations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No destinations yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = OghColors.OnSurfaceMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tap + Add to configure your first RTMP endpoint",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OghColors.OnSurfaceMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(destinations, key = { it.id }) { dest ->
                    DestinationCard(
                        destination = dest,
                        onToggle = { onToggleDestination(dest.id) },
                        onEdit = { onEditDestination(dest.id) },
                        onDelete = { deleteTarget = dest },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { dest ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${dest.name}?") },
            text = { Text("This destination will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteDestination(dest.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = OghColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            },
            containerColor = OghColors.SurfaceContainerHigh,
        )
    }
}

@Composable
private fun DestinationCard(
    destination: Destination,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dotColor = colorFromHex(destination.colorHex, OghColors.Violet)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (destination.enabled) dotColor else OghColors.OnSurfaceMuted,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = destination.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = destination.rtmpUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = OghColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = destination.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OghColors.Violet,
                        uncheckedThumbColor = OghColors.OnSurfaceMuted,
                        uncheckedTrackColor = OghColors.Outline,
                    ),
                )
            }

            if (destination.type == DestinationType.RTMP_MANUAL) {
                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelLarge,
                        color = OghColors.VioletLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onEdit() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = OghColors.Error.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDelete() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
