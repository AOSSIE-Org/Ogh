package com.ogh.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.LatencyMode
import com.ogh.shared.domain.PrivacyStatus
import com.ogh.shared.domain.Resolution
import com.ogh.shared.domain.StreamMetadata
import com.ogh.shared.domain.StreamingProvider
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.ui.theme.OghColors

internal val supportedFrameRates = listOf(15, 24, 25, 30, 50, 60)
internal val supportedVideoBitrates = listOf(
    800_000,
    1_500_000,
    2_500_000,
    4_500_000,
    6_000_000,
    8_000_000,
    12_000_000,
    20_000_000,
    35_000_000,
)

/** Complete non-live configuration surface for destinations, metadata, and video. */
@Composable
fun SettingsScreen(
    videoSettings: VideoSettings,
    metadata: StreamMetadata,
    destinations: List<Destination>,
    onVideoSettingsChanged: (VideoSettings) -> Unit,
    onMetadataChanged: (StreamMetadata) -> Unit,
    onChoosePauseImage: () -> Unit,
    onUseDefaultPauseImage: () -> Unit,
    onNavigateDestinations: () -> Unit,
    onAddDestination: () -> Unit,
    onNavigateAccounts: () -> Unit,
    onConnectProvider: (StreamingProvider) -> Unit,
) {
    val hasDestinations = destinations.isNotEmpty()
    val enabledDestinations = destinations.count(Destination::enabled)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasDestinations) "Settings" else "Set up Ogh",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hasDestinations) {
                StreamDetailsCard(
                    metadata = metadata,
                    onMetadataChanged = onMetadataChanged,
                )
            } else {
                ConnectPlatformsCard(
                    onConnectProvider = onConnectProvider,
                    onAddDestination = onAddDestination,
                )
            }

            NavigationCard(
                destinationCount = destinations.size,
                enabledDestinationCount = enabledDestinations,
                onNavigateAccounts = onNavigateAccounts,
                onNavigateDestinations = if (hasDestinations) {
                    onNavigateDestinations
                } else {
                    onAddDestination
                },
            )

            SettingsSection(title = "Video") {
                ResolutionSetting(
                    current = videoSettings.resolution,
                    onChanged = { onVideoSettingsChanged(videoSettings.copy(resolution = it)) },
                )
                FpsSetting(
                    current = videoSettings.fps,
                    onChanged = { onVideoSettingsChanged(videoSettings.copy(fps = it)) },
                )
                BitrateSetting(
                    current = videoSettings.bitrate,
                    onChanged = { onVideoSettingsChanged(videoSettings.copy(bitrate = it)) },
                )
                ToggleSetting(
                    label = "Lock Orientation",
                    description = "Keep the app in its current orientation",
                    checked = videoSettings.lockOrientation,
                    onChanged = {
                        onVideoSettingsChanged(videoSettings.copy(lockOrientation = it))
                    },
                )
                ToggleSetting(
                    label = "Keep Screen Awake",
                    description = "Prevent the display from sleeping during a stream",
                    checked = videoSettings.keepScreenAwake,
                    onChanged = {
                        onVideoSettingsChanged(videoSettings.copy(keepScreenAwake = it))
                    },
                )
                HorizontalDivider(color = OghColors.Outline)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pause image", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (videoSettings.pauseImageUri == null) {
                            "Ogh logo (default)"
                        } else {
                            "Custom local image"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OghColors.OnSurfaceMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChoosePauseImage) {
                            Text("Choose Image")
                        }
                        if (videoSettings.pauseImageUri != null) {
                            TextButton(onClick = onUseDefaultPauseImage) {
                                Text("Use Logo")
                            }
                        }
                    }
                    Text(
                        text = "Hide Video replaces only the picture; audio remains live.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OghColors.OnSurfaceMuted,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StreamDetailsCard(
    metadata: StreamMetadata,
    onMetadataChanged: (StreamMetadata) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Stream details", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = metadata.title,
                onValueChange = { onMetadataChanged(metadata.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedTextField(
                value = metadata.description,
                onValueChange = { onMetadataChanged(metadata.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(8.dp),
            )
            ChoiceChips(
                label = "Privacy",
                selected = metadata.privacyStatus,
                options = PrivacyStatus.entries,
                optionLabel = PrivacyStatus::displayName,
                onSelected = { onMetadataChanged(metadata.copy(privacyStatus = it)) },
            )
            ChoiceChips(
                label = "Latency",
                selected = metadata.latencyMode,
                options = LatencyMode.entries,
                optionLabel = LatencyMode::displayName,
                onSelected = { onMetadataChanged(metadata.copy(latencyMode = it)) },
            )
            Text(
                text = "These details apply to connected platforms; manual RTMP servers ignore them.",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun ConnectPlatformsCard(
    onConnectProvider: (StreamingProvider) -> Unit,
    onAddDestination: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Connect a platform", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Connect YouTube or Twitch, or add a manual RTMP destination to begin.",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreamingProvider.entries.forEach { provider ->
                    Button(
                        onClick = { onConnectProvider(provider) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = "Connect ${provider.displayName}"
                            },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(provider.displayName, maxLines = 1)
                    }
                }
            }
            OutlinedButton(
                onClick = onAddDestination,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Add manual RTMP")
            }
        }
    }
}

@Composable
private fun NavigationCard(
    destinationCount: Int,
    enabledDestinationCount: Int,
    onNavigateAccounts: () -> Unit,
    onNavigateDestinations: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        SettingsNavigationRow(
            label = "Platforms",
            subtitle = "Connect YouTube or Twitch",
            onClick = onNavigateAccounts,
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), 0.5.dp, OghColors.Outline)
        SettingsNavigationRow(
            label = "Destinations",
            subtitle = when {
                destinationCount == 0 -> "Add a streaming destination"
                enabledDestinationCount == 1 -> "1 enabled of $destinationCount"
                else -> "$enabledDestinationCount enabled of $destinationCount"
            },
            onClick = onNavigateDestinations,
        )
    }
}

@Composable
private fun SettingsNavigationRow(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OghColors.OnSurfaceMuted)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = OghColors.OnSurfaceMuted)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = OghColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ResolutionSetting(current: Resolution, onChanged: (Resolution) -> Unit) {
    PresetDropdown(
        label = "Resolution",
        selected = current,
        options = Resolution.entries,
        optionLabel = Resolution::label,
        onSelected = onChanged,
    )
}

@Composable
private fun FpsSetting(current: Int, onChanged: (Int) -> Unit) {
    PresetDropdown(
        label = "Frame Rate",
        selected = current,
        options = supportedFrameRates,
        optionLabel = { "$it fps" },
        onSelected = onChanged,
    )
}

@Composable
private fun BitrateSetting(current: Int, onChanged: (Int) -> Unit) {
    PresetDropdown(
        label = "Video Bitrate",
        selected = current,
        options = supportedVideoBitrates,
        optionLabel = ::formatVideoBitrate,
        onSelected = onChanged,
    )
}

internal fun formatVideoBitrate(bitrate: Int): String {
    val whole = bitrate / 1_000_000
    val tenths = bitrate % 1_000_000 / 100_000
    return if (tenths == 0) "$whole Mbps" else "$whole.$tenths Mbps"
}

@Composable
private fun <T> PresetDropdown(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(optionLabel(selected), modifier = Modifier.weight(1f))
                Text("⌄", color = OghColors.OnSurfaceMuted)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            expanded = false
                            if (option != selected) onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceChips(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(option) },
                    label = { Text(optionLabel(option)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OghColors.Violet.copy(alpha = 0.2f),
                        selectedLabelColor = OghColors.VioletLight,
                        containerColor = Color.Transparent,
                        labelColor = OghColors.OnSurfaceMuted,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = OghColors.Outline,
                        selectedBorderColor = OghColors.Violet,
                        enabled = true,
                        selected = isSelected,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    label: String,
    description: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChanged)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = OghColors.OnSurfaceMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = OghColors.Violet,
                uncheckedThumbColor = OghColors.OnSurfaceMuted,
                uncheckedTrackColor = OghColors.Outline,
            ),
        )
    }
}
