package com.ogh.app

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource

/**
 * A session-scoped screen source that keeps one VirtualDisplay for the lifetime
 * of a MediaProjection grant. Android 14 permits only one VirtualDisplay per
 * grant, so camera transitions detach its surface instead of destroying and
 * recreating it.
 */
internal class ReusableScreenSource(
    context: Context,
    private val mediaProjection: MediaProjection,
    private val onProjectionStopped: () -> Unit,
) : VideoSource() {
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val handlerThread = HandlerThread("OghScreenSource").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private var virtualDisplay: VirtualDisplay? = null
    private var outputSurface: Surface? = null
    private var closed = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            closeInternal(unregisterCallback = false)
            onProjectionStopped()
        }
    }

    init {
        mediaProjection.registerCallback(projectionCallback, handler)
    }

    fun belongsTo(projection: MediaProjection): Boolean =
        !closed && mediaProjection === projection

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        require(width % 2 == 0 && height % 2 == 0) {
            "Screen width and height must be divisible by 2"
        }
        return !closed
    }

    override fun start(surfaceTexture: SurfaceTexture): Unit = synchronized(this) {
        check(!closed) { "Screen capture is no longer available" }
        this.surfaceTexture = surfaceTexture
        val rotated = rotation == 90 || rotation == 270
        val outputWidth = if (rotated) height else width
        val outputHeight = if (rotated) width else height
        surfaceTexture.setDefaultBufferSize(outputWidth, outputHeight)

        outputSurface?.release()
        val surface = Surface(surfaceTexture)
        val display = virtualDisplay
        if (display == null) {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "OghScreenSource",
                outputWidth,
                outputHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                surface,
                null,
                handler,
            ) ?: error("Failed to create screen capture display")
        } else {
            display.resize(outputWidth, outputHeight, densityDpi)
            display.surface = surface
        }
        outputSurface = surface
    }

    /** Detaches capture while preserving the grant's sole VirtualDisplay. */
    override fun stop(): Unit = synchronized(this) {
        virtualDisplay?.surface = null
        outputSurface?.release()
        outputSurface = null
    }

    /** RootEncoder calls release during source changes; session cleanup owns final release. */
    override fun release() = Unit

    override fun isRunning(): Boolean = synchronized(this) { outputSurface != null }

    override fun getOrientationConfig(): OrientationConfig {
        val rotated = rotation == 90 || rotation == 270
        val portrait = height > width
        return OrientationConfig(0, rotated != portrait, OrientationForced.NONE)
    }

    fun close(): Unit = closeInternal(unregisterCallback = true)

    private fun closeInternal(unregisterCallback: Boolean): Unit = synchronized(this) {
        if (closed) return@synchronized
        closed = true
        outputSurface?.release()
        outputSurface = null
        virtualDisplay?.release()
        virtualDisplay = null
        if (unregisterCallback) {
            runCatching { mediaProjection.unregisterCallback(projectionCallback) }
        }
        handlerThread.quitSafely()
    }
}
