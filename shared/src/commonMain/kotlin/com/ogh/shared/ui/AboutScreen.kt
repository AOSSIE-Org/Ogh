package com.ogh.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogh.shared.ui.theme.OghColors

/**
 * About screen.
 *
 * Displays version, build information, license, project links, and philosophy.
 */
data class AboutUiState(
    val versionName: String,
    val buildType: String,
    val platform: String,
    val minimumPlatform: String,
    val repositoryUrl: String,
)

@Composable
fun AboutScreen(
    state: AboutUiState,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // App name
            Column {
                Text(
                    text = "Ogh",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Open-source mobile live streaming",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OghColors.OnSurfaceMuted,
                )
            }

            // Version info
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InfoRow("Version", state.versionName)
                    InfoRow("Build", state.buildType)
                    InfoRow("Platform", state.platform)
                    InfoRow("Minimum", state.minimumPlatform)
                }
            }

            // Links
            Text(
                text = "LINKS",
                style = MaterialTheme.typography.labelSmall,
                color = OghColors.OnSurfaceMuted,
                letterSpacing = 1.sp,
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinkRow(
                        label = "Source Code",
                        subtitle = state.repositoryUrl.removePrefix("https://").ifBlank { "Unavailable" },
                        enabled = state.repositoryUrl.isNotBlank(),
                    ) {
                        onOpenUrl(state.repositoryUrl)
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        thickness = 0.5.dp,
                        color = OghColors.Outline,
                    )
                    LinkRow(
                        label = "Report Issue",
                        subtitle = "GitHub Issues",
                        enabled = state.repositoryUrl.isNotBlank(),
                    ) {
                        onOpenUrl("${state.repositoryUrl}/issues")
                    }
                }
            }

            Text(
                text = "PRIVACY",
                style = MaterialTheme.typography.labelSmall,
                color = OghColors.OnSurfaceMuted,
                letterSpacing = 1.sp,
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = "Ogh stores streaming credentials only on this device using Android Keystore encryption. Connecting YouTube grants Ogh permission to identify your channel and manage broadcasts you create. Ogh does not operate an analytics, advertising, account, or media-relay service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OghColors.OnSurfaceMuted,
                    modifier = Modifier.padding(18.dp),
                )
            }

            // License
            Text(
                text = "LICENSE",
                style = MaterialTheme.typography.labelSmall,
                color = OghColors.OnSurfaceMuted,
                letterSpacing = 1.sp,
            )
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Apache License 2.0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Free to use, modify, and distribute.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OghColors.OnSurfaceMuted,
                    )
                }
            }

            // Philosophy
            Text(
                text = "No analytics · No telemetry · No ads · No cloud",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OghColors.OnSurfaceMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LinkRow(
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else OghColors.OnSurfaceMuted,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.VioletLight,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = OghColors.OnSurfaceMuted,
        )
    }
}
