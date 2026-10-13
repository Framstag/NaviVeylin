package com.naviveylin.auto

import com.naviveylin.core.DeepLinkDestination
import com.naviveylin.core.DeepLinkResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The car session's decision per parse outcome (spec: auto-deep-links — Deep link parsed into a
 * destination, Short link resolves to a map target, Short link does not resolve). Pure seam: the
 * session itself needs a host-provided `CarContext` and cannot be constructed here.
 */
class DeepLinkDecisionTest {

    @Test
    fun resolvedShortLinkNavigates() {
        val action = deepLinkAction(
            DeepLinkResult.Destination(DeepLinkDestination(48.8584, 2.2945, "Eiffel Tower"))
        )

        assertEquals(DeepLinkAction.Navigate(48.8584, 2.2945), action)
    }

    @Test
    fun placeLinkWithDataCoordinatesNavigates() {
        // What the shared parser yields for a modern Google Maps share link.
        val action = deepLinkAction(
            DeepLinkResult.Destination(DeepLinkDestination(48.8583701, 2.2944813, "Eiffel Tower"))
        )

        assertEquals(DeepLinkAction.Navigate(48.8583701, 2.2944813), action)
    }

    @Test
    fun queryWithoutCoordinatesIsGeocoded() {
        val action = deepLinkAction(
            DeepLinkResult.Destination(DeepLinkDestination(null, null, "Ruhrallee 1"))
        )

        assertEquals(DeepLinkAction.Geocode("Ruhrallee 1"), action)
    }

    @Test
    fun unusableLinkReportsError() {
        // Never Geocode: the link text is not a location name.
        assertEquals(DeepLinkAction.ReportUnusable, deepLinkAction(DeepLinkResult.UnusableLink))
    }

    @Test
    fun noDestinationDoesNothing() {
        assertEquals(DeepLinkAction.Nothing, deepLinkAction(DeepLinkResult.None))
    }
}
