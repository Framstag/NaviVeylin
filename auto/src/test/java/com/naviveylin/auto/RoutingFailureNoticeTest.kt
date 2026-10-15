package com.naviveylin.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.FailureReason
import com.naviveylin.core.NavigationFailure
import com.naviveylin.core.RouteEndpoint
import com.naviveylin.core.hasMapManagerAction
import com.naviveylin.core.navigationFailureText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the car's side of the failure-reporting contract (spec: `routing-failure-reporting` — *Each
 * reason is worded by the surface*, *A reason whose cause the user can act on offers the action*;
 * `cross-variant-ui-parity` — same label, deviate only as much as the platform requires).
 *
 * The car's notice is the same wording the phone shows for the same cause, built from the same shared
 * resource, plus — for a cause the map manager can fix — one statement that the maps belong to the phone,
 * because a car session must not launch a phone screen.
 */
@RunWith(RobolectricTestRunner::class)
class RoutingFailureNoticeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val mapsOnPhone = context.getString(R.string.car_failure_maps_on_phone)

    private fun carText(failure: NavigationFailure): String =
        failureNoticeText(context = context, failure = failure, mapsOnPhoneMessage = mapsOnPhone)

    private fun phoneText(failure: NavigationFailure): String =
        navigationFailureText(
            context = context,
            failure = failure,
            unitMetersRes = R.string.distance_unit_m,
            unitKilometersRes = R.string.distance_unit_km
        )

    private fun failure(reason: FailureReason, distance: Double? = null) = NavigationFailure(
        reason = reason,
        endpoint = when (reason) {
            FailureReason.START_UNROUTABLE -> RouteEndpoint.START
            FailureReason.TARGET_UNROUTABLE -> RouteEndpoint.TARGET
            else -> null
        },
        nearestRoadDistanceMeters = distance
    )

    @Test
    fun theCarLabelEqualsThePhoneLabelForEveryReason() {
        val reasons = listOf(
            FailureReason.ROUTING_DATA_UNAVAILABLE,
            FailureReason.START_UNROUTABLE,
            FailureReason.TARGET_UNROUTABLE,
            FailureReason.NO_ROUTE_FOUND,
            FailureReason.ROUTING_DATA_FAILURE,
            FailureReason.ENGINE_FAILURE,
            FailureReason.GPS_SIGNAL_MISSING,
            FailureReason.UNKNOWN
        )

        reasons.forEach { reason ->
            val car = carText(failure(reason))
            val phone = phoneText(failure(reason))

            if (failure(reason).hasMapManagerAction()) {
                // The one deviation the platform forces is an addition, never a rewording: the driver is
                // told where the action is instead of being offered it.
                assertTrue(
                    "the car states the same cause and adds the phone ($reason): $car",
                    car.startsWith(phone)
                )
            } else {
                assertEquals("the same cause reads the same on both surfaces ($reason)", phone, car)
            }
        }
    }

    @Test
    fun theDistanceIsWordedIdenticallyOnBothSurfaces() {
        val reason = FailureReason.TARGET_UNROUTABLE

        assertEquals(
            "a stated distance is the same label on both surfaces",
            phoneText(failure(reason, distance = 350.0)),
            carText(failure(reason, distance = 350.0))
        )
        assertEquals(
            "and so is a kilometer distance",
            phoneText(failure(reason, distance = 7_000.0)),
            carText(failure(reason, distance = 7_000.0))
        )
    }

    @Test
    fun unusableRoutingDataNamesThePhoneAndLaunchesNothing() {
        listOf(
            FailureReason.ROUTING_DATA_UNAVAILABLE,
            FailureReason.ROUTING_DATA_FAILURE,
            FailureReason.NO_ROUTE_FOUND
        ).forEach { reason ->
            val text = carText(failure(reason))

            assertTrue("the notice names the phone ($reason): $text", text.contains(mapsOnPhone))
            assertTrue(
                "the referral is the last thing the notice says, a statement and not an action the car " +
                    "cannot carry ($reason): $text",
                text.endsWith(mapsOnPhone)
            )
        }

        // The other half of that scenario — the car launches no phone screen — is structural, not
        // assertable here: :auto has no map-manager destination at all, so no code path could launch one.
        // The device task 6.5 confirms the notice on the AAOS AVD.
    }

    @Test
    fun aReasonWithNoActionStatesNoDestinationForIt() {
        // The failing end of a route has no map-manager fix, so the notice must not send the driver to the
        // phone for it.
        val text = carText(failure(FailureReason.TARGET_UNROUTABLE, distance = 7_000.0))

        assertFalse("no phone referral for a cause the maps cannot fix: $text", text.contains(mapsOnPhone))
        assertTrue("the cause itself is stated: $text", text.contains("destination"))
    }

    @Test
    fun aFailureWithNoReasonLeavesTheNoticeWithTheCauseAlone() {
        val text = carText(failure(FailureReason.ENGINE_FAILURE))

        assertEquals(
            "an engine failure reads the same on both surfaces",
            phoneText(failure(FailureReason.ENGINE_FAILURE)),
            text
        )
    }
}
