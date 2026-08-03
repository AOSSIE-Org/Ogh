package org.aossie.ogh.ui

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.aossie.ogh.ScreenCaptureService
import com.ogh.shared.domain.StreamState
import com.ogh.shared.domain.VideoSettings
import com.ogh.shared.ui.theme.OghColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PreviewSurface(
    val surface: Surface,
    val width: Int,
    val height: Int,
)

/** Android preview adapter; capture and rendering remain owned by ScreenCaptureService. */
@Composable
fun StreamPreview(
    videoSettings: VideoSettings,
    streamState: StreamState,
    enabledDestinationCount: Int,
    serviceReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    when {
        streamState == StreamState.PAUSED -> PreviewMessage(
            title = "Video hidden",
            message = "The pause image is live while the stream stays connected.",
            modifier = modifier,
        )
        !videoSettings.source.usesCamera && streamState != StreamState.STREAMING -> PreviewMessage(
            title = "Screen selected",
            message = "Android will ask what to share when you tap Go Live.",
            modifier = modifier,
        )
        videoSettings.source.usesCamera && !hasCameraPermission -> PreviewMessage(
            title = "Camera preview",
            message = "Tap Camera to allow camera access.",
            modifier = modifier,
        )
        else -> CaptureSurface(
            videoSettings = videoSettings,
            streamState = streamState,
            enabledDestinationCount = enabledDestinationCount,
            serviceReady = serviceReady,
            modifier = modifier,
        )
    }
}

@Composable
private fun CaptureSurface(
    videoSettings: VideoSettings,
    streamState: StreamState,
    enabledDestinationCount: Int,
    serviceReady: Boolean,
    modifier: Modifier,
) {
    var previewSurface by remember { mutableStateOf<PreviewSurface?>(null) }
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    val callback = remember {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val view = surfaceView ?: return
                previewSurface = PreviewSurface(
                    holder.surface,
                    view.width.coerceAtLeast(1),
                    view.height.coerceAtLeast(1),
                )
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) {
                previewSurface = PreviewSurface(
                    holder.surface,
                    width.coerceAtLeast(1),
                    height.coerceAtLeast(1),
                )
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                ScreenCaptureService.instance?.detachPreview(holder.surface)
                previewSurface = null
            }
        }
    }

    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view ->
                surfaceView = view
                view.holder.addCallback(callback)
            }
        },
        modifier = modifier.fillMaxSize().background(Color.Black),
    )

    LaunchedEffect(
        serviceReady,
        previewSurface,
        videoSettings.source,
        videoSettings.resolution,
        videoSettings.fps,
        videoSettings.bitrate,
        streamState,
        enabledDestinationCount,
    ) {
        val target = previewSurface ?: return@LaunchedEffect
        if (!serviceReady || !target.surface.isValid) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            ScreenCaptureService.instance?.showPreview(
                surface = target.surface,
                surfaceWidth = target.width,
                surfaceHeight = target.height,
                settings = videoSettings,
                outputCount = enabledDestinationCount.coerceAtLeast(1),
            )
        }
    }

    DisposableEffect(callback) {
        onDispose {
            previewSurface?.surface?.let { ScreenCaptureService.instance?.detachPreview(it) }
            surfaceView?.holder?.removeCallback(callback)
            surfaceView = null
        }
    }
}

@Composable
private fun PreviewMessage(title: String, message: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OghColors.OnSurfaceMuted,
            )
        }
    }
}
