package com.naviveylin.auto

import com.naviveylin.core.DeepLinkDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The car deep-link log line carries the destination's *shape*, never the parsed
 * object (spec: auto-diagnostics — Diagnostics carry no coordinates / Shared location
 * and deep link log identity, not the URI): [DeepLinkDestination]'s `toString()`
 * prints `lat`/`lon`. Pure-seam test — the [NavigationSession] method the line
 * belongs to needs a host-provided `CarContext` and cannot be constructed here.
 */
class DeepLinkLogMessageTest {

    /** A coordinate-shaped token: 1-3 integer digits followed by 4+ decimals. */
    private val coordinateShaped = Regex("""-?\b\d{1,3}\.\d{4,}\b""")

    @Test
    fun coordinateDestinationLogsTheShapeOnly() {
        val message = deepLinkLogMessage(DeepLinkDestination(51.5142, 7.4653, null))

        assertEquals("Deep link parsed: shape=coordinates", message)
        assertFalse("no coordinate in the line: $message", coordinateShaped.containsMatchIn(message))
    }

    @Test
    fun queryDestinationLogsTheShapeOnly() {
        val message = deepLinkLogMessage(DeepLinkDestination(null, null, "Ruhrallee 1"))

        assertEquals("Deep link parsed: shape=query", message)
        assertFalse("the query text is not identity for the log", message.contains("Ruhrallee"))
    }
}
