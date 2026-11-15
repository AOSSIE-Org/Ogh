package com.ogh.shared.ui

import com.ogh.shared.domain.Destination
import com.ogh.shared.domain.DestinationConnectionStatus.CONNECTING
import com.ogh.shared.domain.DestinationConnectionStatus.DISCONNECTED
import com.ogh.shared.domain.DestinationConnectionStatus.FAILED
import com.ogh.shared.domain.DestinationConnectionStatus.LIVE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionDestinationSummaryTest {

    private val a = destination("a", "Studio")
    private val b = destination("b", "Backup")
    private val c = destination("c", "Archive")

    @Test
    fun allLive_reportsCountAndNoProblems() {
        val summary = summarizeSessionDestinations(listOf(a, b), mapOf("a" to LIVE, "b" to LIVE))

        assertEquals("Live · 2 of 2 destinations", summary.liveHeadline)
        assertTrue(summary.problems.isEmpty())
    }

    @Test
    fun partialFailure_namesOnlyTheFailedAndDisconnectedOutputsInListOrder() {
        val summary = summarizeSessionDestinations(
            destinations = listOf(a, b, c),
            statuses = mapOf("c" to DISCONNECTED, "a" to LIVE, "b" to FAILED),
        )

        assertEquals("Live · 1 of 3 destinations", summary.liveHeadline)
        assertEquals(
            listOf(DestinationProblem("Backup", FAILED), DestinationProblem("Archive", DISCONNECTED)),
            summary.problems,
        )
    }

    @Test
    fun connectingOutputs_areNeitherLiveNorProblems() {
        val summary = summarizeSessionDestinations(listOf(a, b), mapOf("a" to CONNECTING, "b" to LIVE))

        assertEquals(1, summary.live)
        assertTrue(summary.problems.isEmpty())
    }

    @Test
    fun singleDestination_headlineHasNoCount() {
        val summary = summarizeSessionDestinations(listOf(a), mapOf("a" to LIVE))

        assertEquals("Live", summary.liveHeadline)
    }

    @Test
    fun destinationsOutsideTheSession_areIgnored() {
        val summary = summarizeSessionDestinations(listOf(a, b), mapOf("a" to LIVE, "gone" to FAILED))

        assertEquals(1, summary.total)
        assertTrue(summary.problems.isEmpty())
    }

    @Test
    fun noStatuses_isAnEmptySummary() {
        val summary = summarizeSessionDestinations(listOf(a, b), emptyMap())

        assertEquals(0, summary.total)
        assertEquals("Live", summary.liveHeadline)
    }

    private fun destination(id: String, name: String) = Destination(
        id = id,
        name = name,
        rtmpUrl = "rtmp://example.com/live",
        streamKey = "key",
    )
}
