package com.ogh.shared.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogh.shared.ui.theme.OghColors
import com.ogh.shared.domain.Destination

/**
 * Screen for adding or editing an RTMP destination.
 *
 * Collects validated connection details and an identifying color for one
 * user-managed RTMP endpoint.
 */
@Composable
fun DestinationFormScreen(
    existing: Destination? = null,
    onSubmit: (
        existing: Destination?,
        name: String,
        url: String,
        key: String,
        colorHex: String,
    ) -> String?,
    onBack: () -> Unit,
) {
    val isEditing = existing != null

    // Generate a random name for new destinations
    val defaultName = rememberSaveable(existing?.id) {
        val adjectives = listOf("Red", "Blue", "Fast", "Chill", "Live", "Night", "Epic", "Pixel")
        val nouns = listOf("Stream", "Cast", "Wave", "Beam", "Flow", "Pulse", "Feed", "Link")
        "${adjectives.random()} ${nouns.random()}"
    }

    // Non-secret input survives activity recreation. The stream key is deliberately
    // kept out of saved instance state so credentials stay only in encrypted storage.
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: defaultName) }
    var url by rememberSaveable(existing?.id) { mutableStateOf(existing?.rtmpUrl.orEmpty()) }
    var key by remember(existing?.id) { mutableStateOf(existing?.streamKey ?: "") }
    var showKey by remember(existing?.id) { mutableStateOf(false) }
    var colorHex by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.colorHex ?: Destination.DEFAULT_COLOR_HEX)
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val colorOptions = listOf(
        Destination.DEFAULT_COLOR_HEX,
        "#F43F9E",
        "#10B981",
        "#F59E0B",
        "#3B82F6",
        "#EF4444",
    )

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
                    text = if (isEditing) "Edit Destination" else "Add Destination",
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
            Spacer(Modifier.height(4.dp))

            // Connection section
            Text(
                text = "CONNECTION",
                style = MaterialTheme.typography.labelSmall,
                color = OghColors.OnSurfaceMuted,
                letterSpacing = 1.sp,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text("Destination Name") },
                placeholder = { Text("e.g. YouTube, Twitch, Kick") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = formFieldColors(),
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; errorMessage = null },
                label = { Text("RTMP URL") },
                placeholder = { Text("rtmp://a.rtmp.youtube.com/live2") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = formFieldColors(),
            )

            OutlinedTextField(
                value = key,
                onValueChange = { key = it; errorMessage = null },
                label = { Text("Stream Key") },
                placeholder = { Text("xxxx-xxxx-xxxx-xxxx") },
                singleLine = true,
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = { showKey = !showKey },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        // Label on the text so screen readers announce one phrase on the button.
                        Text(
                            text = if (showKey) "Hide" else "Show",
                            color = OghColors.VioletLight,
                            modifier = Modifier.semantics {
                                contentDescription =
                                    if (showKey) "Hide stream key" else "Show stream key"
                            },
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = formFieldColors(),
            )

            Spacer(Modifier.height(4.dp))

            // Color
            Text(
                text = "COLOR",
                style = MaterialTheme.typography.labelSmall,
                color = OghColors.OnSurfaceMuted,
                letterSpacing = 1.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                colorOptions.forEach { hex ->
                    val color = colorFromHex(hex, OghColors.Violet)
                    val isSelected = hex == colorHex

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { colorHex = hex },
                            )
                            .semantics { contentDescription = "Color $hex" },
                    ) {
                        // Decorative swatch; the wrapper carries the radio semantics.
                        Surface(
                            modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                            shape = RoundedCornerShape(8.dp),
                            color = color,
                            border = if (isSelected) {
                                ButtonDefaults.outlinedButtonBorder(enabled = true)
                            } else null,
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Error
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OghColors.Error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            Spacer(Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    val error = onSubmit(existing, name, url, key, colorHex)
                    if (error != null) {
                        errorMessage = error
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Submit Destination" },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OghColors.Violet),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Add Destination",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun formFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = OghColors.Violet,
    unfocusedBorderColor = OghColors.Outline,
    focusedLabelColor = OghColors.VioletLight,
    unfocusedLabelColor = OghColors.OnSurfaceMuted,
    cursorColor = OghColors.Violet,
)
