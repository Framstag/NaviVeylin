package com.naviveylin.ui.route

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.RouteCallback
import com.naviveylin.R
import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.core.hasMapManagerAction
import com.naviveylin.core.navigationFailureText
import com.naviveylin.core.navigationFailureWordingRes
import com.naviveylin.navigation.navigationFailureOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that a failure is worded by the surface from its reason (spec: `routing-failure-reporting` —
 * *Each reason is worded by the surface*, *A reason whose cause the user can act on offers the action*,
 * *A reason that concerns one end of the route identifies that end*; `i18n-l10n` — *A failure from native
 * code is worded by the surface*).
 *
 * The property under test is that the wording follows the **cause**, not a sentence that happened to
 * travel with the failure: two different causes must not read the same, the device language decides the
 * text, and a value the wording needs (here the distance to the nearest road) is substituted into a
 * resource rather than assembled in code.
 */
@RunWith(RobolectricTestRunner::class)
class RoutingFailureWordingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The route-calculation causes — the ones this change introduced a reason for. */
    private val routeReasons = listOf(
        FailureReason.ROUTING_DATA_UNAVAILABLE,
        FailureReason.START_UNROUTABLE,
        FailureReason.TARGET_UNROUTABLE,
        FailureReason.NO_ROUTE_FOUND,
        FailureReason.ROUTING_DATA_FAILURE
    )

    private fun failureOf(reason: FailureReason,
                          endpoint: RouteEndpoint? = null,
                          nearestRoadDistanceMeters: Double? = null,
                          surfaceMessage: String? = null) =
        NavigationFailure(
            reason = reason,
            endpoint = endpoint,
            nearestRoadDistanceMeters = nearestRoadDistanceMeters,
            surfaceMessage = surfaceMessage
        )

    private fun textOf(failure: NavigationFailure): String =
        navigationFailureText(
            context = context,
            failure = failure,
            unitMetersRes = R.string.distance_unit_m,
            unitKilometersRes = R.string.distance_unit_km
        )

    @Test
    fun everyReasonResolvesToItsOwnResource() {
        FailureReason.entries.forEach { reason ->
            assertTrue(
                "every reason has wording ($reason)",
                navigationFailureWordingRes(failureOf(reason)) != 0
            )
        }

        // Distinct causes must not read the same: the wording follows the cause, which is the whole
        // point of reporting a reason instead of one sentence.
        val routeWording = routeReasons.map { navigationFailureWordingRes(failureOf(it)) }
        assertEquals(
            "each route cause has its own wording",
            routeReasons.size, routeWording.toSet().size
        )
    }

    @Test
    fun theStartAndTheTargetReasonsAreDifferentResources() {
        assertNotEquals(
            "the two unattachable ends are told apart",
            navigationFailureWordingRes(failureOf(FailureReason.START_UNROUTABLE)),
            navigationFailureWordingRes(failureOf(FailureReason.TARGET_UNROUTABLE))
        )
    }

    @Config(qualifiers = "de")
    @Test
    fun aFailureOnAGermanDeviceShowsTheGermanResource() {
        val failure = failureOf(FailureReason.TARGET_UNROUTABLE)

        val german = textOf(failure)
        val english = context.resources.configuration.locales[0].language

        assertEquals("the device locale is German", "de", english)
        assertTrue("a German device renders German wording", german.isNotBlank())
        assertNotEquals(
            "the wording is not the English one",
            "The destination has no connection to the road network here.",
            german
        )
    }

    @Test
    fun aMessageNamingARoadIsAResourceWithASubstitutedValue() {
        // No distance: the wording states the cause alone.
        val plain = textOf(failureOf(FailureReason.TARGET_UNROUTABLE))
        // A distance: the variant that states it, with the value substituted into the resource.
        val withDistance = textOf(failureOf(FailureReason.TARGET_UNROUTABLE, nearestRoadDistanceMeters = 7_000.0))

        assertNotEquals("a known distance changes the wording", plain, withDistance)
        assertTrue("the distance is substituted, not appended: $withDistance", withDistance.contains("7"))
        assertTrue(
            "the unit comes from the surface's own resource: $withDistance",
            withDistance.contains(context.getString(R.string.distance_unit_km, "7.0"))
        )
    }

    @Test
    fun unusableRoutingDataOffersTheMapManager() {
        listOf(
            FailureReason.ROUTING_DATA_UNAVAILABLE,
            FailureReason.ROUTING_DATA_FAILURE,
            // A missing map region is a map-manager fix too: the reason's own message names it as the
            // likely cause, so the surface must not leave the user without the way to act on it.
            FailureReason.NO_ROUTE_FOUND
        ).forEach { reason ->
            assertTrue(
                "a map-manager cause offers its action ($reason)",
                failureOf(reason).hasMapManagerAction()
            )
        }
    }

    @Test
    fun noRouteFoundOffersNoAction() {
        // The reasons no map manager can fix: the point is off the network, or the route is simply
        // longer than the profile allows.
        listOf(
            FailureReason.TARGET_UNROUTABLE,
            FailureReason.START_UNROUTABLE
        ).forEach { reason ->
            assertFalse(
                "a cause the map manager cannot fix offers no action ($reason)",
                failureOf(reason).hasMapManagerAction()
            )
        }
    }

    @Test
    fun theDestinationReasonNamesTheDestination() {
        val failure = navigationFailureOf(
            kind = RouteCallback.KIND_TARGET_UNROUTABLE,
            detail = "No routable node near destination"
        )

        assertEquals(FailureReason.TARGET_UNROUTABLE, failure.reason)
        assertEquals(RouteEndpoint.TARGET, failure.endpoint)
        assertTrue("the wording names the destination", textOf(failure).contains("destination"))
    }

    @Test
    fun theFailureCarriesTheNearestRoadDistance() {
        val withDistance = navigationFailureOf(
            kind = RouteCallback.KIND_TARGET_UNROUTABLE,
            detail = "No routable node near destination",
            nearestRoadDistanceMeters = 7_000.0
        )
        assertEquals(7_000.0, withDistance.nearestRoadDistanceMeters!!, 1e-9)

        // A distance the router did not find is "unknown", never "the nearest road is 0 m away".
        listOf(Double.NaN, 0.0, -1.0).forEach { unknown ->
            assertEquals(
                "an unusable distance is unknown ($unknown)",
                null,
                navigationFailureOf(kind = RouteCallback.KIND_TARGET_UNROUTABLE,
                    nearestRoadDistanceMeters = unknown).nearestRoadDistanceMeters
            )
        }
    }

    @Test
    fun anUnknownReasonShowsTheUnspecifiedFailureResource() {
        // A kind this build does not know (a newer native library) is an unspecified failure, not a
        // crash and not silence.
        val failure = navigationFailureOf(kind = 9_999, detail = "a cause from the future")

        assertEquals(FailureReason.UNKNOWN, failure.reason)
        assertEquals(
            com.naviveylin.core.R.string.navigation_failure_unspecified,
            navigationFailureWordingRes(failure)
        )
        assertTrue("it still says something", textOf(failure).isNotBlank())
    }

    @Test
    fun aSurfaceWordedFailureKeepsItsOwnWording() {
        // The carve-out of the same-label rule: a failure whose wording an existing decision gives to one
        // surface travels as that surface's own message (guidelines/UI.md §1, its parity table).
        val failure = failureOf(
            reason = FailureReason.UNKNOWN,
            surfaceMessage = "Shared link could not be read",
            nearestRoadDistanceMeters = 7_000.0
        )

        assertEquals("Shared link could not be read", textOf(failure))
    }

    @Test
    fun theRouterIsNotAskedToDistinguishATooLongRoute() {
        // Measured on the device, 2026-10-13: the router's cost-limit stop also fires for short trips whose
        // endpoints' parts of the network do not join — 24 of 416 points swept across Dortmund's old town
        // had discarded extensions at the cost limit, on trips of at most 1.2 km air-line distance. A
        // "no route found — the distance is longer than this vehicle profile allows" would have been false
        // for every one of them, so no such reason exists: both read as no route found, and the wording must
        // not blame the distance.
        assertEquals(
            FailureReason.NO_ROUTE_FOUND,
            navigationFailureOf(kind = RouteCallback.KIND_NO_ROUTE_FOUND).reason
        )
        assertFalse(
            "the wording does not blame the distance: ${textOf(failureOf(FailureReason.NO_ROUTE_FOUND))}",
            textOf(failureOf(FailureReason.NO_ROUTE_FOUND)).contains("distance")
        )
    }

    @Test
    fun aProducerThatOwnsItsWordingRaisesItAsTheFailureMessage() {
        val failure = navigationFailureOf(kind = RouteCallback.KIND_UNKNOWN, detail = "native detail")
        assertEquals(FailureReason.UNKNOWN, failure.reason)
        assertEquals(SurfaceOrigin.ENGINE, failure.origin)
        assertEquals("the native description is a log detail", "native detail", failure.detail)
    }
}
