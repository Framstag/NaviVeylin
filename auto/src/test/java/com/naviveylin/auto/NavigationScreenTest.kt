package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [NavigationScreen.backCallback] (spec: auto/navigation-view —
 * "Leave navigation at any time"): the system-back affordance during
 * navigation must stop navigation. The screen itself needs a live CarContext
 * (same constraint as [MapScreenTest]); the callback factory is the testable
 * seam.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationScreenTest {

    @Test
    fun backCallbackInvokesHandler() {
        var invoked = false
        val callback = NavigationScreen.backCallback { invoked = true }
        callback.handleOnBackPressed()
        assertTrue("back must invoke the stop handler", invoked)
    }

    @Test
    fun backCallbackEnabledByDefault() {
        val callback = NavigationScreen.backCallback {}
        assertTrue("back must be enabled during navigation", callback.isEnabled)
    }

    // ── shouldRotateHeadingUp (spec: auto/map-pan — rotation frozen while panned) ──

    @Test
    fun panningFreezesHeadingUpRotation() {
        // While panned the map must not rotate under the gesture, even with
        // a valid bearing and heading-up enabled.
        assertFalse(NavigationScreen.shouldRotateHeadingUp(panning = true, navNorthUp = false, bearing = 90.0))
    }

    @Test
    fun headingUpRotatesWhenNotPanning() {
        assertTrue(NavigationScreen.shouldRotateHeadingUp(panning = false, navNorthUp = false, bearing = 90.0))
    }

    @Test
    fun northUpOrUnknownBearingDoNotRotate() {
        assertFalse(NavigationScreen.shouldRotateHeadingUp(panning = false, navNorthUp = true, bearing = 90.0))
        assertFalse(NavigationScreen.shouldRotateHeadingUp(panning = false, navNorthUp = false, bearing = -1.0))
    }

    // ── autoZoomTarget (spec: auto/map-pan — auto-zoom suspended while
    // panned; design D2 — the zoom feed is gated, not just the commit) ──

    @Test
    fun panningSuppressesAutoZoomFeed() {
        // The regression case: a suspended controller re-engages on a
        // speed-band crossing (highway speed fed from a city band) and would
        // return a zoom — the panning gate must return null regardless, so
        // the commit block can never run mid-pan.
        val controller = AutoZoomController()
        controller.onSpeed(45.0) // city band
        controller.suspend()
        assertNull(
            NavigationScreen.autoZoomTarget(
                panning = true, autoZoomEnabled = true, speedKmH = 100.0, controller = controller
            )
        )
    }

    @Test
    fun notPanningDelegatesToOnSpeed() {
        // Two fresh controllers, same input: the gate must not swallow the
        // call — the result equals a direct onSpeed consultation.
        val direct = AutoZoomController().onSpeed(100.0)
        val viaGate = NavigationScreen.autoZoomTarget(
            panning = false, autoZoomEnabled = true, speedKmH = 100.0, AutoZoomController()
        )
        assertEquals(direct, viaGate)
    }

    @Test
    fun disabledAutoZoomReturnsNull() {
        assertNull(
            NavigationScreen.autoZoomTarget(
                panning = false, autoZoomEnabled = false, speedKmH = 100.0, AutoZoomController()
            )
        )
    }

    @Test
    fun invalidSpeedReturnsNull() {
        assertNull(
            NavigationScreen.autoZoomTarget(
                panning = false, autoZoomEnabled = true, speedKmH = -1.0, AutoZoomController()
            )
        )
    }

    // ── tripTextFor (design D5: street name in host ETA card when the map
    // label is not safe) ──

    @Test
    fun tripTextNullWhenMapLabelSafe() {
        // Safe host geometry: the map label carries the name, the ETA card
        // shows no trip text.
        assertNull(NavigationScreen.tripTextFor(mapLabelSafe = true, streetName = "Hauptstraße"))
    }

    @Test
    fun tripTextCarriesNameWhenMapLabelUnsafe() {
        // Unsafe host geometry (full-surface/empty areas): the street name
        // goes into the host ETA card so it is never covered.
        assertEquals("Hauptstraße", NavigationScreen.tripTextFor(mapLabelSafe = false, streetName = "Hauptstraße"))
    }

    @Test
    fun tripTextNullWhenNameUnknown() {
        // No street name resolved yet: nothing to show in the card.
        assertNull(NavigationScreen.tripTextFor(mapLabelSafe = false, streetName = null))
    }
}
