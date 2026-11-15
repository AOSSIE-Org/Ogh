package com.ogh.shared.ui

import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationConnectionStatus

/** A destination whose output is not delivering the session, shown by name. */
internal data class DestinationProblem(val name: String, val status: DestinationConnectionStatus)

/**
 * Per-destination session state condensed for the Preview status card: a count
 * while everything is healthy, and names only for outputs that need attention.
 */
internal data class SessionDestinationSummary(
    val total: Int,
    val live: Int,
    val problems: List<DestinationProblem>,
) {
    /** Status-card headline while streaming; a single destination needs no count. */
    val liveHeadline: String
        get() = if (total <= 1) "Live" else "Live · $live of $total destinations"
}

/**
 * Summarizes [statuses] for the destinations that belong to the session, in the
 * order they appear in [destinations]. Destinations without a status are not
 * part of the session and are ignored.
 */
internal fun summarizeSessionDestinations(
    destinations: List<Destination>,
    statuses: Map<String, DestinationConnectionStatus>,
): SessionDestinationSummary {
    val tracked = destinations.mapNotNull { destination ->
        statuses[destination.id]?.let { destination to it }
    }
    return SessionDestinationSummary(
        total = tracked.size,
        live = tracked.count { (_, status) -> status == DestinationConnectionStatus.LIVE },
        problems = tracked
            .filter { (_, status) ->
                status == DestinationConnectionStatus.FAILED ||
                    status == DestinationConnectionStatus.DISCONNECTED
            }
            .map { (destination, status) -> DestinationProblem(destination.name, status) },
    )
}
