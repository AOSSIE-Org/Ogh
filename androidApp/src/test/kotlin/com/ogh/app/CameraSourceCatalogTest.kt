package com.ogh.app

import android.content.pm.ActivityInfo
import android.hardware.camera2.CameraCharacteristics
import com.ogh.shared.domain.VideoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraSourceCatalogTest {

    @Test
    fun orientationLock_appliesOnlyAfterPersistedSettingsLoad() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(lockOrientation = true, settingsLoaded = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LOCKED,
            requestedOrientationFor(lockOrientation = true, settingsLoaded = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            requestedOrientationFor(lockOrientation = false, settingsLoaded = true),
        )
    }

    @Test
    fun physicalOrientation_quantizesWithHysteresis() {
        assertEquals(
            PhysicalOrientation.PORTRAIT,
            resolvePhysicalOrientation(44, current = null),
        )
        assertEquals(
            PhysicalOrientation.LANDSCAPE_RIGHT,
            resolvePhysicalOrientation(90, current = null),
        )
        assertEquals(
            PhysicalOrientation.PORTRAIT,
            resolvePhysicalOrientation(50, PhysicalOrientation.PORTRAIT),
        )
        assertEquals(
            PhysicalOrientation.LANDSCAPE_RIGHT,
            resolvePhysicalOrientation(60, PhysicalOrientation.PORTRAIT),
        )
        assertEquals(
            PhysicalOrientation.LANDSCAPE_RIGHT,
            resolvePhysicalOrientation(40, PhysicalOrientation.LANDSCAPE_RIGHT),
        )
        assertEquals(
            PhysicalOrientation.PORTRAIT,
            resolvePhysicalOrientation(30, PhysicalOrientation.LANDSCAPE_RIGHT),
        )
    }

    @Test
    fun physicalOrientation_handlesReverseAndUnknownReadings() {
        assertEquals(
            PhysicalOrientation.LANDSCAPE_LEFT,
            resolvePhysicalOrientation(270, current = null),
        )
        assertEquals(
            PhysicalOrientation.REVERSE_PORTRAIT,
            resolvePhysicalOrientation(180, PhysicalOrientation.PORTRAIT),
        )
        assertEquals(
            PhysicalOrientation.REVERSE_PORTRAIT,
            resolvePhysicalOrientation(-1, PhysicalOrientation.REVERSE_PORTRAIT),
        )
    }

    @Test
    fun physicalOrientation_mapsDisplayFallbackAndViewportOrientation() {
        assertEquals(PhysicalOrientation.PORTRAIT, displayFallbackOrientation(90))
        assertEquals(PhysicalOrientation.LANDSCAPE_LEFT, displayFallbackOrientation(0))
        assertEquals(PhysicalOrientation.REVERSE_PORTRAIT, displayFallbackOrientation(270))
        assertEquals(PhysicalOrientation.LANDSCAPE_RIGHT, displayFallbackOrientation(180))

        assertTrue(PhysicalOrientation.PORTRAIT.isPortrait)
        assertTrue(PhysicalOrientation.REVERSE_PORTRAIT.isPortrait)
        assertFalse(PhysicalOrientation.LANDSCAPE_RIGHT.isPortrait)
        assertTrue(isPortraitSurface(width = 720, height = 1280))
        assertFalse(isPortraitSurface(width = 1280, height = 720))
    }

    @Test
    fun cameraCatalog_containsOnlyAvailableSupportedFacings() {
        assertEquals(
            listOf(VideoSource.SCREEN, VideoSource.BACK_CAMERA, VideoSource.FRONT_CAMERA),
            videoSourcesForLensFacings(
                listOf(
                    CameraCharacteristics.LENS_FACING_FRONT,
                    CameraCharacteristics.LENS_FACING_BACK,
                    CameraCharacteristics.LENS_FACING_BACK,
                    CameraCharacteristics.LENS_FACING_EXTERNAL,
                ),
            ),
        )
    }

    @Test
    fun cameraCatalog_alwaysKeepsScreenAvailable() {
        assertEquals(listOf(VideoSource.SCREEN), videoSourcesForLensFacings(emptyList()))
    }

    @Test
    fun cameraControl_selectsFirstCameraFromScreenThenFlips() {
        val sources = listOf(
            VideoSource.SCREEN,
            VideoSource.BACK_CAMERA,
            VideoSource.FRONT_CAMERA,
        )

        assertEquals(VideoSource.BACK_CAMERA, nextCameraSource(VideoSource.SCREEN, sources))
        assertEquals(
            VideoSource.FRONT_CAMERA,
            nextCameraSource(VideoSource.BACK_CAMERA, sources),
        )
        assertEquals(
            VideoSource.BACK_CAMERA,
            nextCameraSource(VideoSource.FRONT_CAMERA, sources),
        )
    }

    @Test
    fun cameraControl_handlesSingleOrMissingCamera() {
        assertEquals(
            VideoSource.FRONT_CAMERA,
            nextCameraSource(
                VideoSource.FRONT_CAMERA,
                listOf(VideoSource.SCREEN, VideoSource.FRONT_CAMERA),
            ),
        )
        assertEquals(null, nextCameraSource(VideoSource.SCREEN, listOf(VideoSource.SCREEN)))
    }
}
