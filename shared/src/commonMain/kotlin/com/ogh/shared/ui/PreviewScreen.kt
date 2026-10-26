package com.ogh.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.StreamingStats
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import com.ogh.shared.ui.theme.OghBrandGradient
import com.ogh.shared.ui.theme.OghColors
import com.ogh.shared.ui.theme.OghSpacing

/** Platform-neutral state for the focused stream preview and its live controls. */
data class PreviewUiState(
    val streamState: StreamState,
    val statusMessage: String,
    val stats: StreamingStats,
    val destinations: List<Destination>,
    val videoSettings: VideoSettings,
    val audioSettings: AudioSettings,
    val availableVideoSources: List<VideoSource>,
    val isSourceSwitching: Boolean = false,
)

private val ControlHeight = 56.dp

/**
 * The root preview screen. Configuration lives on Settings; this screen keeps
 * only the selected-source preview and controls needed immediately before or
 * during a broadcast.
 */
@Composable
fun PreviewScreen(
    state: PreviewUiState,
    previewContent: @Composable (Modifier) -> Unit,
    onSelectScreen: () -> Unit,
    onSelectCamera: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onPauseVideo: () -> Unit,
    onResumeVideo: () -> Unit,
    onOpenDestinations: () -> Unit,
) {
    val isPreparing = state.streamState == StreamState.PREPARING
    val isStreaming = state.streamState == StreamState.STREAMING
    val isPaused = state.streamState == StreamState.PAUSED
    val isActive = state.streamState.locksConfiguration
    val isCamera = state.videoSettings.source.usesCamera
    val cameraCount = state.availableVideoSources.count(VideoSource::usesCamera)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSingleControlRow = maxWidth > maxHeight
        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                PreviewControls(
                    selectedSource = state.videoSettings.source,
                    audioSettings = state.audioSettings,
                    destinations = state.destinations,
                    cameraCount = cameraCount,
                    streamState = state.streamState,
                    isSourceSwitching = state.isSourceSwitching,
                    useSingleRow = useSingleControlRow,
                    onSelectScreen = onSelectScreen,
                    onSelectCamera = onSelectCamera,
                    onToggleMicrophone = onToggleMicrophone,
                    onToggleSystemAudio = onToggleSystemAudio,
                    onStartStream = onStartStream,
                    onStopStream = onStopStream,
                    onPauseVideo = onPauseVideo,
                    onResumeVideo = onResumeVideo,
                    onOpenDestinations = onOpenDestinations,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                previewContent(Modifier.fillMaxSize())

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = OghSpacing.Large, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(OghSpacing.Medium),
                ) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 38.dp)) {
                        OghMark(
                            modifier = Modifier
                                .size(38.dp)
                                .align(Alignment.Center),
                        )
                        if (isActive) {
                            LiveBadge(
                                streamState = state.streamState,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                    if (state.streamState != StreamState.IDLE) {
                        SessionStatus(
                            statusMessage = if (state.isSourceSwitching) {
                                "Switching source…"
                            } else {
                                state.statusMessage
                            },
                            elapsedTime = if (isActive) state.stats.formattedTime else null,
                            bitrate = if (isActive) state.stats.formattedBitrate else null,
                        )
                    }
                }

                if (!isStreaming && !isPaused && !isPreparing) {
                    Text(
                        text = if (isCamera) "Camera preview" else "Screen capture",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = OghSpacing.Medium)
                            .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                            .padding(horizontal = OghSpacing.Medium, vertical = OghSpacing.Small),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewControls(
    selectedSource: VideoSource,
    audioSettings: AudioSettings,
    destinations: List<Destination>,
    cameraCount: Int,
    streamState: StreamState,
    isSourceSwitching: Boolean,
    useSingleRow: Boolean,
    onSelectScreen: () -> Unit,
    onSelectCamera: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onPauseVideo: () -> Unit,
    onResumeVideo: () -> Unit,
    onOpenDestinations: () -> Unit,
) {
    val isPreparing = streamState == StreamState.PREPARING
    val isPaused = streamState == StreamState.PAUSED
    val isLive = streamState == StreamState.STREAMING || isPaused
    val canChangeSource = !isPreparing && !isSourceSwitching
    val enabledDestinations = destinations.filter(Destination::enabled)
    var confirmStop by remember { mutableStateOf(false) }

    // A session that ends on its own must not leave a stale confirmation behind.
    LaunchedEffect(isLive) { if (!isLive) confirmStop = false }

    val inputControls: @Composable RowScope.() -> Unit = {
        SourceControls(
            selectedSource = selectedSource,
            cameraCount = cameraCount,
            enabled = canChangeSource,
            onSelectScreen = onSelectScreen,
            onSelectCamera = onSelectCamera,
        )
        SelectionButton(
            label = "Mic",
            icon = OghIcon.MICROPHONE,
            selected = audioSettings.enableMicrophone,
            enabled = !isPreparing,
            onClick = onToggleMicrophone,
            modifier = Modifier.weight(0.7f),
        )
        SelectionButton(
            label = "System Audio",
            icon = OghIcon.SPEAKER,
            selected = audioSettings.enableSystemAudio,
            enabled = !isPreparing,
            onClick = onToggleSystemAudio,
            modifier = Modifier.weight(1.3f),
        )
    }
    val actionControls: @Composable RowScope.() -> Unit = {
        ActionButtons(
            streamState = streamState,
            isLive = isLive,
            canGoLive = enabledDestinations.isNotEmpty(),
            isPaused = isPaused,
            onStartStream = onStartStream,
            onRequestStop = { confirmStop = true },
            onPauseVideo = onPauseVideo,
            onResumeVideo = onResumeVideo,
        )
    }

    Surface(color = Color.Black, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = OghSpacing.Large, vertical = OghSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DestinationStrip(
                enabledDestinations = enabledDestinations,
                onOpenDestinations = onOpenDestinations.takeUnless { streamState.locksConfiguration },
            )
            if (useSingleRow) {
                ControlRow {
                    inputControls()
                    actionControls()
                }
            } else {
                ControlRow(content = inputControls)
                ControlRow(content = actionControls)
            }
        }
    }

    if (confirmStop) {
        StopConfirmation(
            destinationCount = enabledDestinations.size,
            onConfirm = {
                confirmStop = false
                onStopStream()
            },
            onDismiss = { confirmStop = false },
        )
    }
}

@Composable
private fun ControlRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Shows where Go Live will publish, or why it cannot start. */
@Composable
private fun DestinationStrip(
    enabledDestinations: List<Destination>,
    onOpenDestinations: (() -> Unit)?,
) {
    if (enabledDestinations.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "No destination enabled",
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.Warning,
                modifier = Modifier.weight(1f),
            )
            if (onOpenDestinations != null) {
                TextButton(onClick = onOpenDestinations) { Text("Set up") }
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(OghSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Sending to",
            style = MaterialTheme.typography.labelMedium,
            color = OghColors.OnSurfaceMuted,
        )
        enabledDestinations.forEach { destination ->
            Row(
                modifier = Modifier
                    .background(OghColors.SurfaceContainerHigh, MaterialTheme.shapes.small)
                    .padding(horizontal = OghSpacing.Small, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val provider = destination.type.provider
                if (provider != null) {
                    ProviderLogo(provider, size = 14.dp)
                } else {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colorFromHex(destination.colorHex, OghColors.Violet), CircleShape),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = OghColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.SourceControls(
    selectedSource: VideoSource,
    cameraCount: Int,
    enabled: Boolean,
    onSelectScreen: () -> Unit,
    onSelectCamera: () -> Unit,
) {
    val canFlip = selectedSource.usesCamera && cameraCount > 1
    SelectionButton(
        label = "Screen",
        icon = OghIcon.SCREEN,
        selected = selectedSource == VideoSource.SCREEN,
        enabled = enabled,
        onClick = onSelectScreen,
        modifier = Modifier.weight(1f),
    )
    SelectionButton(
        label = if (canFlip) "Flip Camera" else "Camera",
        icon = if (canFlip) OghIcon.FLIP_CAMERA else OghIcon.CAMERA,
        selected = selectedSource.usesCamera,
        enabled = enabled && cameraCount > 0,
        onClick = onSelectCamera,
        modifier = Modifier.weight(1.3f),
    )
}

/** The action area: Go Live when idle, Hide/Show Video and Stop when live. */
@Composable
private fun RowScope.ActionButtons(
    streamState: StreamState,
    isLive: Boolean,
    canGoLive: Boolean,
    isPaused: Boolean,
    onStartStream: () -> Unit,
    onRequestStop: () -> Unit,
    onPauseVideo: () -> Unit,
    onResumeVideo: () -> Unit,
) {
    if (isLive) {
        OutlinedButton(
            onClick = if (isPaused) onResumeVideo else onPauseVideo,
            modifier = Modifier.weight(1f).height(ControlHeight),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, OghColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            OghIconImage(
                icon = if (isPaused) OghIcon.EYE else OghIcon.EYE_OFF,
                tint = LocalContentColor.current,
            )
            Spacer(Modifier.size(OghSpacing.Small))
            Text(if (isPaused) "Show Video" else "Hide Video", maxLines = 1)
        }
        Button(
            onClick = onRequestStop,
            modifier = Modifier.weight(1f).height(ControlHeight),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = OghColors.Error,
                contentColor = Color.White,
            ),
        ) {
            OghIconImage(icon = OghIcon.STOP, tint = Color.White, size = 18.dp)
            Spacer(Modifier.size(OghSpacing.Small))
            Text("Stop", maxLines = 1)
        }
    } else {
        GoLiveButton(
            streamState = streamState,
            enabled = canGoLive,
            onClick = onStartStream,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StopConfirmation(
    destinationCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End broadcast?") },
        text = {
            Text(
                if (destinationCount == 1) {
                    "Publishing stops on your destination."
                } else {
                    "Publishing stops on all $destinationCount destinations."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onConfirm()
            }) {
                Text("End Stream", color = OghColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep Streaming") }
        },
        containerColor = OghColors.SurfaceContainerHigh,
    )
}

/** A source or audio toggle: icon above a short label, with selection exposed to accessibility. */
@Composable
private fun SelectionButton(
    label: String,
    icon: OghIcon,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ControlHeight)
            .semantics { this.selected = selected },
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) OghColors.Violet.copy(alpha = 0.24f) else Color.Transparent,
            contentColor = if (selected) OghColors.VioletLight else Color.White,
        ),
        border = BorderStroke(1.dp, if (selected) OghColors.Violet else OghColors.Outline),
        contentPadding = PaddingValues(horizontal = OghSpacing.ExtraSmall, vertical = 6.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OghIconImage(icon = icon, tint = LocalContentColor.current, size = 18.dp)
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun LiveBadge(streamState: StreamState, modifier: Modifier = Modifier) {
    val isPreparing = streamState == StreamState.PREPARING
    val isPaused = streamState == StreamState.PAUSED
    val color by animateColorAsState(
        targetValue = if (isPreparing || isPaused) OghColors.Warning else OghColors.Error,
        animationSpec = tween(600),
        label = "live_color",
    )
    val pulse by rememberInfiniteTransition(label = "live_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live_dot",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.18f), MaterialTheme.shapes.large)
            .padding(horizontal = 10.dp, vertical = OghSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color.copy(alpha = if (streamState == StreamState.STREAMING) pulse else 1f),
                    CircleShape,
                ),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = when {
                isPreparing -> "PREPARING"
                isPaused -> "VIDEO HIDDEN"
                else -> "LIVE"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionStatus(
    statusMessage: String,
    elapsedTime: String?,
    bitrate: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.68f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(OghSpacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (elapsedTime != null) StatItem("Time", elapsedTime)
            if (bitrate != null) StatItem("Bitrate", bitrate)
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = OghColors.OnSurfaceMuted,
            letterSpacing = 1.sp,
        )
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun GoLiveButton(
    streamState: StreamState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val isPreparing = streamState == StreamState.PREPARING
    val clickable = enabled && !isPreparing
    val shape = MaterialTheme.shapes.large
    val background = when {
        clickable -> Brush.horizontalGradient(OghBrandGradient)
        isPreparing -> SolidColor(OghColors.VioletDark)
        else -> SolidColor(OghColors.SurfaceContainerHighest)
    }
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        enabled = clickable,
        modifier = modifier
            .height(ControlHeight)
            .background(background, shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = if (isPreparing) Color.White else OghColors.OnSurfaceMuted,
        ),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        if (isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Box(Modifier.size(10.dp).background(LocalContentColor.current, CircleShape))
        }
        Spacer(Modifier.size(OghSpacing.Small))
        Text(
            text = if (isPreparing) "Preparing…" else "Go Live",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Platform-neutral single-stroke Ogh mark used by every Compose target. */
@Composable
fun OghMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = "Ogh" }) {
        val scaleX = size.width / 54f
        val scaleY = size.height / 60f
        val path = Path().apply {
            moveTo(4f * scaleX, 3f * scaleY)
            lineTo(49f * scaleX, 30f * scaleY)
            lineTo(4f * scaleX, 57f * scaleY)
            lineTo(4f * scaleX, 15f * scaleY)
            lineTo(35f * scaleX, 33f * scaleY)
            lineTo(12f * scaleX, 47f * scaleY)
            lineTo(12f * scaleX, 27f * scaleY)
            lineTo(24f * scaleX, 34f * scaleY)
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(OghBrandGradient),
            style = Stroke(
                width = 6f * minOf(scaleX, scaleY),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
