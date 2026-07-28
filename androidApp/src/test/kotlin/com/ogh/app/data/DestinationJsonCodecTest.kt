package com.ogh.app.data

import com.ogh.shared.domain.Destination
import org.junit.Test
import kotlin.test.assertEquals

class DestinationJsonCodecTest {

    @Test
    fun roundTrip_preservesSensitiveAndDisplayFields() {
        val destinations = listOf(
            Destination(
                id = "one",
                name = "Primary",
                rtmpUrl = "rtmps://example.com/live",
                streamKey = "private-key",
                enabled = false,
                colorHex = "#123456",
            ),
        )

        assertEquals(destinations, DestinationJsonCodec.decode(DestinationJsonCodec.encode(destinations)))
    }

    @Test
    fun decode_missingOptionalFields_usesSafeDefaults() {
        val decoded = DestinationJsonCodec.decode(
            """[{"id":"one","name":"Primary","rtmpUrl":"rtmp://example/live","streamKey":"key"}]""",
        ).single()

        assertEquals(true, decoded.enabled)
        assertEquals(Destination.DEFAULT_COLOR_HEX, decoded.colorHex)
    }
}
