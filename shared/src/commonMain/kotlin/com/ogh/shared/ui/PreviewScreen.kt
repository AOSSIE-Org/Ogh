package com.ogh.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogh.shared.domain.AudioSettings
import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.StreamingStats
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.domain.VideoSource
import com.ogh.shared.ui.theme.OghColors

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
    onNavigateSettings: () -> Unit,
) {
    val isPreparing = state.streamState == StreamState.PREPARING
    val isStreaming = state.streamState == StreamState.STREAMING
    val isPaused = state.streamState == StreamState.PAUSED
    val isActive = state.streamState.locksConfiguration
    val isCamera = state.videoSettings.source.usesCamera
    val cameraCount = state.availableVideoSources.count(VideoSource::usesCamera)
    val enabledDestinations = state.destinations.count(Destination::enabled)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSingleControlRow = maxWidth > maxHeight
        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                PreviewControls(
                    selectedSource = state.videoSettings.source,
                    audioSettings = state.audioSettings,
                    cameraCount = cameraCount,
                    streamState = state.streamState,
                    isSourceSwitching = state.isSourceSwitching,
                    useSingleRow = useSingleControlRow,
                    canGoLive = enabledDestinations > 0,
                    onSelectScreen = onSelectScreen,
                    onSelectCamera = onSelectCamera,
                    onToggleMicrophone = onToggleMicrophone,
                    onToggleSystemAudio = onToggleSystemAudio,
                    onStartStream = onStartStream,
                    onStopStream = onStopStream,
                    onPauseVideo = onPauseVideo,
                    onResumeVideo = onResumeVideo,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                previewContent(Modifier.fillMaxSize())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    OghMark(
                        modifier = Modifier
                            .size(38.dp)
                            .align(Alignment.Center),
                    )
                    if (!isActive) {
                        TextButton(
                            onClick = onNavigateSettings,
                            modifier = Modifier.align(Alignment.CenterEnd).height(48.dp),
                        ) {
                            Text("Settings")
                        }
                    } else {
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
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 66.dp, start = 16.dp, end = 16.dp),
                    )
                }

                if (!isStreaming && !isPaused && !isPreparing) {
                    Text(
                        text = if (isCamera) "Camera preview" else "Screen capture",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
    cameraCount: Int,
    streamState: StreamState,
    isSourceSwitching: Boolean,
    useSingleRow: Boolean,
    canGoLive: Boolean,
    onSelectScreen: () -> Unit,
    onSelectCamera: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleSystemAudio: () -> Unit,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onPauseVideo: () -> Unit,
    onResumeVideo: () -> Unit,
) {
    val isPreparing = streamState == StreamState.PREPARING
    val isStreaming = streamState == StreamState.STREAMING
    val isPaused = streamState == StreamState.PAUSED
    val isLive = isStreaming || isPaused
    val canChangeSource = !isPreparing && !isSourceSwitching

    Surface(color = Color.Black, tonalElevation = 0.dp) {
        val containerModifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
        if (useSingleRow) {
            ControlRow(modifier = containerModifier) {
                SourceControls(
                    selectedSource = selectedSource,
                    cameraCount = cameraCount,
                    enabled = canChangeSource,
                    onSelectScreen = onSelectScreen,
                    onSelectCamera = onSelectCamera,
                )
                SelectionButton(
                    label = "Mic",
                    selected = audioSettings.enableMicrophone,
                    enabled = !isPreparing,
                    onClick = onToggleMicrophone,
                    modifier = Modifier.weight(0.8f),
                )
                SelectionButton(
                    label = "Audio",
                    selected = audioSettings.enableSystemAudio,
                    enabled = !isPreparing,
                    onClick = onToggleSystemAudio,
                    modifier = Modifier.weight(0.8f),
                )
                ActionButtons(
                    streamState = streamState,
                    isLive = isLive,
                    canGoLive = canGoLive,
                    isPaused = isPaused,
                    onStartStream = onStartStream,
                    onStopStream = onStopStream,
                    onPauseVideo = onPauseVideo,
                    onResumeVideo = onResumeVideo,
                )
            }
        } else {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlRow {
                    SourceControls(
                        selectedSource = selectedSource,
                        cameraCount = cameraCount,
                        enabled = canChangeSource,
                        onSelectScreen = onSelectScreen,
                        onSelectCamera = onSelectCamera,
                    )
                    SelectionButton(
                        label = "Mic",
                        selected = audioSettings.enableMicrophone,
                        enabled = !isPreparing,
                        onClick = onToggleMicrophone,
                        modifier = Modifier.weight(1f),
                    )
                    SelectionButton(
                        label = "System Audio",
                        selected = audioSettings.enableSystemAudio,
                        enabled = !isPreparing,
                        onClick = onToggleSystemAudio,
                        modifier = Modifier.weight(1.2f),
                    )
                }
                ControlRow {
                    ActionButtons(
                        streamState = streamState,
                        isLive = isLive,
                        canGoLive = canGoLive,
                        isPaused = isPaused,
                        onStartStream = onStartStream,
                        onStopStream = onStopStream,
                        onPauseVideo = onPauseVideo,
                        onResumeVideo = onResumeVideo,
                    )
                }
            }
        }
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

@Composable
private fun RowScope.SourceControls(
    selectedSource: VideoSource,
    cameraCount: Int,
    enabled: Boolean,
    onSelectScreen: () -> Unit,
    onSelectCamera: () -> Unit,
) {
    SelectionButton(
        label = "Screen",
        selected = selectedSource == VideoSource.SCREEN,
        enabled = enabled,
        onClick = onSelectScreen,
        modifier = Modifier.weight(1f),
    )
    SelectionButton(
        label = if (selectedSource.usesCamera && cameraCount > 1) "Flip Camera" else "Camera",
        selected = selectedSource.usesCamera,
        enabled = enabled && cameraCount > 0,
        onClick = onSelectCamera,
        modifier = Modifier.weight(1f),
    )
}

/** The right-hand action area: Go Live when idle, Hide+Stop when live. */
@Composable
private fun RowScope.ActionButtons(
    streamState: StreamState,
    isLive: Boolean,
    canGoLive: Boolean,
    isPaused: Boolean,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onPauseVideo: () -> Unit,
    onResumeVideo: () -> Unit,
) {
    if (isLive) {
        OutlinedButton(
            onClick = if (isPaused) onResumeVideo else onPauseVideo,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(if (isPaused) "Show Video" else "Hide Video", maxLines = 1)
        }
        Button(
            onClick = onStopStream,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OghColors.Error),
        ) {
            Text("Stop", color = Color.White)
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
private fun SelectionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) OghColors.Violet.copy(alpha = 0.24f) else Color.Transparent,
            contentColor = if (selected) OghColors.VioletLight else Color.White,
        ),
        border = BorderStroke(1.dp, if (selected) OghColors.Violet else OghColors.Outline),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
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
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    val isPreparing = streamState == StreamState.PREPARING
    Button(
        onClick = onClick,
        enabled = enabled && !isPreparing,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OghColors.Violet,
            disabledContainerColor = OghColors.Violet.copy(alpha = 0.25f),
        ),
        contentPadding = PaddingValues(horizontal = 6.dp),
    ) {
        if (isPreparing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = if (isPreparing) "Preparing…" else "Go Live",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
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
            brush = Brush.linearGradient(listOf(OghColors.Pink, Color(0xFF8B5CF6))),
            style = Stroke(
                width = 6f * minOf(scaleX, scaleY),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
