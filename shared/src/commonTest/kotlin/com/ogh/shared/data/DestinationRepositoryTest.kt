package com.ogh.shared.data

import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.StreamingProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DestinationRepositoryTest {

    @Test
    fun providerDestination_isUniqueAndRemovedByProvider() {
        val repository = DestinationRepository()

        repository.addProviderDestination(StreamingProvider.YOUTUBE)
        repository.addProviderDestination(StreamingProvider.YOUTUBE)
        repository.addProviderDestination(StreamingProvider.TWITCH)
        repository.removeProviderDestination(StreamingProvider.YOUTUBE)

        assertEquals(listOf("Twitch"), repository.destinations.value.map(Destination::name))
    }

    @Test
    fun restore_keepsOnlyManualDestinationsWithoutPersistingAgain() {
        val repository = DestinationRepository()
        var persisted: List<Destination>? = null
        repository.onChanged = { persisted = it }
        val manual = destination(id = "manual")
        val provider = destination(id = "provider", type = DestinationType.TWITCH_OAUTH)

        repository.restoreManualDestinations(listOf(manual, provider))

        assertEquals(listOf(manual), repository.destinations.value)
        assertNull(persisted)
    }

    @Test
    fun userMutation_updatesStateAndNotifiesPersistenceAdapter() {
        val repository = DestinationRepository()
        var persisted: List<Destination> = emptyList()
        repository.onChanged = { persisted = it }
        val destination = destination(id = "manual")

        repository.addDestination(destination)
        repository.toggleEnabled(destination.id)

        assertFalse(repository.getById(destination.id)!!.enabled)
        assertEquals(repository.destinations.value, persisted)
    }

    private fun destination(
        id: String,
        type: DestinationType = DestinationType.RTMP_MANUAL,
    ) = Destination(
        id = id,
        name = id,
        rtmpUrl = "rtmps://example.com/live",
        streamKey = "key",
        type = type,
    )
}
