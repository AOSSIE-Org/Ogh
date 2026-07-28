package com.ogh.shared.data

import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationType
import com.ogh.shared.domain.StreamingProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns the observable destination collection without depending on platform storage. */
class DestinationRepository {

    private val _destinations = MutableStateFlow<List<Destination>>(emptyList())
    val destinations: StateFlow<List<Destination>> = _destinations.asStateFlow()

    /** Persistence adapters may observe user-initiated collection changes. */
    var onChanged: ((List<Destination>) -> Unit)? = null

    fun getEnabledDestinations(): List<Destination> =
        _destinations.value.filter(Destination::enabled)

    fun addDestination(destination: Destination) = update { it + destination }

    fun addProviderDestination(provider: StreamingProvider) {
        val destination = providerDestination(provider)
        update { current ->
            if (current.any { it.type == destination.type }) current else current + destination
        }
    }

    fun removeProviderDestination(provider: StreamingProvider) {
        val type = destinationType(provider)
        update { current -> current.filterNot { it.type == type } }
    }

    fun updateDestination(destination: Destination) = update { list ->
        list.map { if (it.id == destination.id) destination else it }
    }

    fun removeDestination(id: String) = update { list -> list.filterNot { it.id == id } }

    fun toggleEnabled(id: String) = update { list ->
        list.map { if (it.id == id) it.copy(enabled = !it.enabled) else it }
    }

    fun getById(id: String): Destination? = _destinations.value.find { it.id == id }

    fun count(): Int = _destinations.value.size

    /** Provider entries are reconstructed from connected accounts, never persisted here. */
    fun restoreManualDestinations(destinations: List<Destination>) {
        _destinations.value = destinations.filter { it.type == DestinationType.RTMP_MANUAL }
    }

    private fun update(transform: (List<Destination>) -> List<Destination>) {
        _destinations.update(transform)
        onChanged?.invoke(_destinations.value)
    }

    private fun providerDestination(provider: StreamingProvider): Destination = Destination(
        id = "provider-${provider.name.lowercase()}",
        name = provider.displayName,
        rtmpUrl = "Managed by connected account",
        streamKey = "",
        colorHex = provider.colorHex,
        type = destinationType(provider),
    )

    private fun destinationType(provider: StreamingProvider): DestinationType = when (provider) {
        StreamingProvider.YOUTUBE -> DestinationType.YOUTUBE_OAUTH
        StreamingProvider.TWITCH -> DestinationType.TWITCH_OAUTH
    }
}
