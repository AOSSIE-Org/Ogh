package com.ogh.shared.domain

/** Pure destination selection used before the Android capture service starts. */
object StreamEndpointPlanner {

    fun enabledManualDestinations(destinations: List<Destination>): List<Destination> =
        destinations.filter { it.enabled && it.type == DestinationType.RTMP_MANUAL }

    /**
     * Builds the ordered list of enabled RTMP endpoints.
     *
     * Manual destinations carry their endpoint in the model. Provider endpoints are
     * short-lived values resolved by the platform immediately before a session.
     */
    fun enabledEndpoints(
        destinations: List<Destination>,
        providerEndpoints: Map<DestinationType, String>,
    ): List<String> = destinations.mapNotNull { destination ->
        if (!destination.enabled) return@mapNotNull null
        when (destination.type) {
            DestinationType.RTMP_MANUAL -> destination.fullEndpoint
            else -> providerEndpoints[destination.type]
        }
    }
}
