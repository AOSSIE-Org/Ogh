package com.ogh.app

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener

private const val ORIENTATION_TRANSITION_DEGREES = 60

/** Cardinal physical orientations used independently of Android display rotation. */
internal enum class PhysicalOrientation(val degrees: Int) {
    PORTRAIT(0),
    LANDSCAPE_RIGHT(90),
    REVERSE_PORTRAIT(180),
    LANDSCAPE_LEFT(270),
    ;

    val isPortrait: Boolean
        get() = this == PORTRAIT || this == REVERSE_PORTRAIT


    companion object {
        fun fromDegrees(degrees: Int): PhysicalOrientation = entries.first { it.degrees == degrees }
    }
}

/**
 * Quantizes sensor degrees with a 15-degree hysteresis margin around each
 * 45-degree cardinal boundary, avoiding rapid flips while a device is tilted.
 */
internal fun resolvePhysicalOrientation(
    sensorDegrees: Int,
    current: PhysicalOrientation?,
): PhysicalOrientation? {
    if (sensorDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) return current
    val normalized = ((sensorDegrees % 360) + 360) % 360
    val candidateDegrees = (((normalized + 45) / 90) % 4) * 90
    val candidate = PhysicalOrientation.fromDegrees(candidateDegrees)
    if (current == null || candidate == current) return candidate

    val directDistance = kotlin.math.abs(normalized - current.degrees)
    val distanceFromCurrent = minOf(directDistance, 360 - directDistance)
    return if (distanceFromCurrent >= ORIENTATION_TRANSITION_DEGREES) candidate else current
}

/** Converts RootEncoder's display-derived camera angle into physical cardinal form. */
internal fun displayFallbackOrientation(cameraOrientation: Int): PhysicalOrientation {
    val deviceDegrees = if (cameraOrientation == 0) 270 else cameraOrientation - 90
    return PhysicalOrientation.fromDegrees(deviceDegrees)
}

/** Keeps the latest physical orientation available across preview surface detach/reattach. */
internal class PhysicalOrientationTracker(
    context: Context,
    initial: PhysicalOrientation,
    private val onChanged: (PhysicalOrientation) -> Unit,
) {
    @Volatile
    var current: PhysicalOrientation = initial
        private set

    private var started = false
    private val listener = object : OrientationEventListener(
        context.applicationContext,
        SensorManager.SENSOR_DELAY_NORMAL,
    ) {
        override fun onOrientationChanged(orientation: Int) {
            val resolved = resolvePhysicalOrientation(orientation, current) ?: return
            if (resolved == current) return
            current = resolved
            onChanged(resolved)
        }
    }

    fun start() {
        if (started) return
        started = true
        onChanged(current)
        if (listener.canDetectOrientation()) listener.enable()
    }

    fun stop() {
        if (!started) return
        listener.disable()
        started = false
    }
}

/** The preview viewport follows its surface, not physical device or stream orientation. */
internal fun isPortraitSurface(width: Int, height: Int): Boolean = height >= width
