package com.ogh.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogh.shared.ui.theme.OghColors
import com.ogh.shared.domain.ProviderAccount
import com.ogh.shared.domain.StreamingProvider

/** A provider connect/disconnect operation currently in progress. */
data class ProviderOperationState(
    val provider: StreamingProvider,
    val kind: Kind,
    /** Code the user confirms on the provider's page (Twitch device authorization). */
    val userCode: String? = null,
    val verificationUri: String? = null,
) {
    enum class Kind { CONNECTING, DISCONNECTING }

    /** Only device-code polling can be abandoned by Ogh; browser sign-in ends in the browser. */
    val isCancellable: Boolean
        get() = kind == Kind.CONNECTING && userCode != null
}

/**
 * Account management screen.
 *
 * Shows connected streaming provider accounts and allows
 * connecting new ones or disconnecting existing ones.
 */
@Composable
fun AccountsScreen(
    connectedAccounts: Map<StreamingProvider, ProviderAccount>,
    operation: ProviderOperationState?,
    onConnect: (StreamingProvider) -> Unit,
    onDisconnect: (StreamingProvider) -> Unit,
    onCancelOperation: () -> Unit,
    onOpenVerificationPage: (String) -> Unit,
    onBack: () -> Unit,
) {
    var disconnectTarget by remember { mutableStateOf<StreamingProvider?>(null) }

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
                BackButton(onClick = onBack)
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
                ProviderCard(
                    provider = provider,
                    account = connectedAccounts[provider],
                    operation = operation?.takeIf { it.provider == provider },
                    actionsEnabled = operation == null,
                    onConnect = { onConnect(provider) },
                    onDisconnect = { disconnectTarget = provider },
                    onCancel = onCancelOperation,
                    onOpenVerificationPage = onOpenVerificationPage,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    disconnectTarget?.let { provider ->
        AlertDialog(
            onDismissRequest = { disconnectTarget = null },
            title = { Text("Disconnect ${provider.displayName}?") },
            text = {
                Text(
                    "Ogh will revoke its access to your ${provider.displayName} account " +
                        "and remove the ${provider.displayName} destination.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    disconnectTarget = null
                    onDisconnect(provider)
                }) {
                    Text("Disconnect", color = OghColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { disconnectTarget = null }) {
                    Text("Cancel")
                }
            },
            containerColor = OghColors.SurfaceContainerHigh,
        )
    }
}

@Composable
private fun ProviderCard(
    provider: StreamingProvider,
    account: ProviderAccount?,
    operation: ProviderOperationState?,
    actionsEnabled: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
    onOpenVerificationPage: (String) -> Unit,
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
            // Decorative: the provider name is the card's visible label.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OghColors.SurfaceContainerHighest)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                ProviderLogo(provider, size = 24.dp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = when {
                    operation?.kind == ProviderOperationState.Kind.CONNECTING -> "Connecting…"
                    operation?.kind == ProviderOperationState.Kind.DISCONNECTING -> "Disconnecting…"
                    account != null && account.userName.isNotBlank() -> account.userName
                    isConnected -> "Connected"
                    else -> "Not connected"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OghColors.OnSurfaceMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            when {
                operation != null && operation.isCancellable -> OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics {
                            contentDescription = "Cancel ${provider.displayName} connection"
                        },
                    )
                }

                operation != null -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "${provider.displayName} in progress" },
                    strokeWidth = 2.dp,
                    color = OghColors.VioletLight,
                )

                isConnected -> OutlinedButton(
                    onClick = onDisconnect,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Disconnect ${provider.displayName}" },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                }

                else -> Button(
                    onClick = onConnect,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Connect ${provider.displayName}" },
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

        if (operation?.userCode != null) {
            DeviceCodePanel(
                provider = provider,
                userCode = operation.userCode,
                verificationUri = operation.verificationUri,
                onOpenVerificationPage = onOpenVerificationPage,
            )
        } else if (operation?.kind == ProviderOperationState.Kind.CONNECTING) {
            Text(
                text = "Finish signing in to ${provider.displayName} in your browser.",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

/** Keeps the device-authorization code visible until the user approves or cancels. */
@Composable
private fun DeviceCodePanel(
    provider: StreamingProvider,
    userCode: String,
    verificationUri: String?,
    onOpenVerificationPage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(color = OghColors.Outline)
        Text(
            text = "Confirm this code on ${provider.displayName}:",
            style = MaterialTheme.typography.bodySmall,
            color = OghColors.OnSurfaceMuted,
        )
        SelectionContainer {
            Text(
                text = userCode,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = "${provider.displayName} code " +
                        userCode.toCharArray().joinToString(" ")
                },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).clearAndSetSemantics {},
                strokeWidth = 2.dp,
                color = OghColors.VioletLight,
            )
            Text(
                text = "Waiting for approval…",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            if (verificationUri != null) {
                TextButton(
                    onClick = { onOpenVerificationPage(verificationUri) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Open ${provider.displayName}", color = OghColors.VioletLight)
                }
            }
        }
    }
}
