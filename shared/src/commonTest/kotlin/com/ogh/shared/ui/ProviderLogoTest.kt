package com.ogh.shared.ui

import com.ogh.shared.data.DestinationRepository
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.StreamingProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderLogoTest {
    @Test
    fun providerDestinationsResolveToTheirProvider() {
        assertEquals(StreamingProvider.YOUTUBE, DestinationType.YOUTUBE_OAUTH.provider)
        assertEquals(StreamingProvider.TWITCH, DestinationType.TWITCH_OAUTH.provider)
    }

    @Test
    fun connectedProviderDestinationsShowTheirOwnLogo() {
        StreamingProvider.entries.forEach { provider ->
            val repository = DestinationRepository()
            repository.addProviderDestination(provider)

            assertEquals(provider, repository.destinations.value.single().type.provider)
        }
    }

    @Test
    fun manualDestinationsHaveNoProvider() {
        assertNull(DestinationType.RTMP_MANUAL.provider)
    }
}
