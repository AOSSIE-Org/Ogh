package com.ogh.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogh.shared.ui.theme.OghColors
import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider

/**
 * Account management screen.
 *
 * Shows connected streaming provider accounts and allows
 * connecting new ones or disconnecting existing ones.
 */
@Composable
fun AccountsScreen(
    connectedAccounts: Map<StreamingProvider, ProviderAccount>,
    onConnect: (StreamingProvider) -> Unit,
    onDisconnect: (StreamingProvider) -> Unit,
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
                    text = "Platforms",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connect your streaming platforms to manage broadcasts directly from Ogh — no need to visit platform dashboards.",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            StreamingProvider.entries.forEach { provider ->
                val account = connectedAccounts[provider]
                ProviderCard(
                    provider = provider,
                    account = account,
                    onConnect = { onConnect(provider) },
                    onDisconnect = { onDisconnect(provider) },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    provider: StreamingProvider,
    account: ProviderAccount?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = account != null
    val providerColor = colorFromHex(provider.colorHex, OghColors.Violet)

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Provider color dot
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(providerColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.displayName.first().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = providerColor,
                )
            }

            Spacer(Modifier.width(14.dp))

            // Provider info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (account != null && account.userName.isNotBlank()) {
                    Text(
                        text = account.userName,
                        style = MaterialTheme.typography.bodySmall,
                        color = OghColors.OnSurfaceMuted,
                    )
                } else if (!isConnected) {
                    Text(
                        text = "Not connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = OghColors.OnSurfaceMuted,
                    )
                }
            }

            // Connect / Disconnect button
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.semantics {
                        contentDescription = "Disconnect ${provider.displayName}"
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.semantics {
                        contentDescription = "Connect ${provider.displayName}"
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = providerColor,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Connect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
