package com.ogh.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamEndpointPlannerTest {

    @Test
    fun endpointPlan_combinesEnabledManualAndResolvedProviderDestinationsInOrder() {
        val manual = destination("manual", DestinationType.RTMP_MANUAL)
        val disabled = destination("disabled", DestinationType.RTMP_MANUAL, enabled = false)
        val twitch = destination("twitch", DestinationType.TWITCH_OAUTH)

        assertEquals(
            listOf(
                "rtmps://example.com/live/key",
                "rtmps://live.twitch.tv/app/provider-key",
            ),
            StreamEndpointPlanner.enabledEndpoints(
                destinations = listOf(manual, disabled, twitch),
                providerEndpoints = mapOf(
                    DestinationType.TWITCH_OAUTH to
                        "rtmps://live.twitch.tv/app/provider-key",
                ),
            ),
        )
    }

    @Test
    fun endpointPlan_omitsEnabledProviderWithoutResolvedEndpoint() {
        val youtube = destination("youtube", DestinationType.YOUTUBE_OAUTH)

        assertTrue(StreamEndpointPlanner.enabledEndpoints(listOf(youtube), emptyMap()).isEmpty())
    }

    @Test
    fun manualSelection_excludesDisabledAndProviderDestinations() {
        val enabled = destination("enabled", DestinationType.RTMP_MANUAL)
        val disabled = destination("disabled", DestinationType.RTMP_MANUAL, enabled = false)
        val youtube = destination("youtube", DestinationType.YOUTUBE_OAUTH)

        assertEquals(
            listOf(enabled),
            StreamEndpointPlanner.enabledManualDestinations(listOf(enabled, disabled, youtube)),
        )
    }

    @Test
    fun activeSessionStates_lockConfigurationOnlyWhilePreparationOrTransmissionContinues() {
        assertTrue(StreamState.PREPARING.locksConfiguration)
        assertTrue(StreamState.STREAMING.locksConfiguration)
        assertTrue(StreamState.PAUSED.locksConfiguration)
        assertFalse(StreamState.IDLE.locksConfiguration)
        assertFalse(StreamState.STOPPED.locksConfiguration)
        assertFalse(StreamState.ERROR.locksConfiguration)
    }

    private fun destination(
        id: String,
        type: DestinationType,
        enabled: Boolean = true,
    ) = Destination(
        id = id,
        name = id,
        rtmpUrl = "rtmps://example.com/live",
        streamKey = "key",
        enabled = enabled,
        type = type,
    )
}
