package com.naviveylin.auto

import com.framstag.libosmscout.client.CurrentRoadInfo
import com.naviveylin.core.AutoFixDerivation
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.NavigationState
import com.naviveylin.core.VehicleAnchorPosition
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

    // ── navigationBackBehavior (design D3, spec: auto/navigation-view —
    // "Leave navigation at any time"; back must never be a dead no-op) ──

    @Test
    fun backDuringNavigationStopsNavigation() {
        var stopped = false
        var left = false
        NavigationScreen.navigationBackBehavior(
            isNavigating = true,
            onStopNavigation = { stopped = true },
            onLeaveNavigationView = { left = true }
        )
        assertTrue("back during navigation must stop navigation", stopped)
        assertFalse("back during navigation must not leave the view directly", left)
    }

    @Test
    fun backWhenNotNavigatingLeavesView() {
        var stopped = false
        var left = false
        NavigationScreen.navigationBackBehavior(
            isNavigating = false,
            onStopNavigation = { stopped = true },
            onLeaveNavigationView = { left = true }
        )
        assertTrue("back with navigation inactive must leave the view", left)
        assertFalse("back with navigation inactive must not call stop navigation", stopped)
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

    // ── settingsDiffer (spec: auto/navigation-view — routing anchor applies
    // on resume without a maneuver change) ──

    @Test
    fun settingsDifferDetectsNewRoutingAnchorWithoutStateChange() {
        val settings = AutoSettings(routingAnchorId = VehicleAnchorPosition.BOTTOM_RIGHT.id)
        assertTrue(
            NavigationScreen.settingsDiffer(
                settings,
                laneHintsEnabled = true,
                navNorthUp = false,
                autoZoomEnabled = true,
                overspeedWarningDeltaKmh = 5,
                routingAnchor = VehicleAnchorPosition.DEFAULT
            )
        )
    }

    @Test
    fun settingsDifferIsFalseWhenNothingChanged() {
        val settings = AutoSettings()
        assertFalse(
            NavigationScreen.settingsDiffer(
                settings,
                laneHintsEnabled = true,
                navNorthUp = false,
                autoZoomEnabled = true,
                overspeedWarningDeltaKmh = 5,
                routingAnchor = VehicleAnchorPosition.DEFAULT
            )
        )
    }

    @Test
    fun northUpOrUnknownBearingDoNotRotate() {
        assertFalse(NavigationScreen.shouldRotateHeadingUp(panning = false, navNorthUp = true, bearing = 90.0))
        assertFalse(NavigationScreen.shouldRotateHeadingUp(panning = false, navNorthUp = false, bearing = -1.0))
    }

    // ── streetNameFromState (spec: auto/navigation-view — street from the
    // route's way, not an area search) ──

    @Test
    fun streetNameFromState_usesRoadInfoRefAndName() {
        val state = NavigationState(currentRoadInfo = CurrentRoadInfo("B 1", "highway_primary", "Hauptstrasse"))
        assertEquals("B 1 Hauptstrasse", NavigationScreen.streetNameFromState(state))
    }

    @Test
    fun streetNameFromState_refOnly() {
        val state = NavigationState(currentRoadInfo = CurrentRoadInfo("A 44", "highway_motorway", ""))
        assertEquals("A 44", NavigationScreen.streetNameFromState(state))
    }

    @Test
    fun streetNameFromState_nullWhenNoRoadInfo() {
        assertNull(NavigationScreen.streetNameFromState(NavigationState()))
        assertNull(NavigationScreen.streetNameFromState(null))
    }

    // ── autoZoomTarget (spec: auto/map-pan — auto-zoom suspended while
    // panned; design D2 — the zoom feed is gated, not just the commit). The rule itself is ONE
    // shared function (`FreeDrivingScreen.autoZoomTarget`); these cases pin the navigation
    // surface's contract against it. ──

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
            FreeDrivingScreen.autoZoomTarget(
                panning = true, autoZoomEnabled = true, speedKmH = 100.0, controller = controller
            )
        )
    }

    @Test
    fun notPanningDelegatesToOnSpeed() {
        // Two fresh controllers, same input: the gate must not swallow the
        // call — the result equals a direct onSpeed consultation.
        val direct = AutoZoomController().onSpeed(100.0)
        val viaGate = FreeDrivingScreen.autoZoomTarget(
            panning = false, autoZoomEnabled = true, speedKmH = 100.0, AutoZoomController()
        )
        assertEquals(direct, viaGate)
    }

    @Test
    fun disabledAutoZoomReturnsNull() {
        assertNull(
            FreeDrivingScreen.autoZoomTarget(
                panning = false, autoZoomEnabled = false, speedKmH = 100.0, AutoZoomController()
            )
        )
    }

    @Test
    fun unknownSpeedFallsBackToTheSeededDefault() {
        // A negative (unknown) speed is NOT a no-op: it resolves to the controller's seeded
        // 20 km/h default, so the navigation surface gets its "reasonable initial zoom"
        // (spec: auto-speed-zoom — Speed unknown) instead of keeping the default map zoom.
        assertEquals(
            16.0,
            FreeDrivingScreen.autoZoomTarget(
                panning = false, autoZoomEnabled = true, speedKmH = -1.0, AutoZoomController()
            ) ?: Double.NaN,
            1e-9
        )
    }

    // ── ETA-card street name (design D1: ALWAYS in the host travel-estimate
    // card, never on the map surface) ──

    @Test
    fun etaCardTextAlwaysCarriesStreetName() {
        // The card owns the name unconditionally — safe host geometry no
        // longer moves it off the card (replaced the gated tripTextFor).
        assertEquals("Hauptstraße", NavigationScreen.etaCardText("Hauptstraße"))
    }

    @Test
    fun etaCardTextNullWhenNoStreetName() {
        // Spec: "No street name when unnamed" — no trip text without a name.
        assertNull(NavigationScreen.etaCardText(null))
        assertNull(NavigationScreen.etaCardText(""))
    }

    @Test
    fun streetNameChangeAlwaysRebuildsTemplate() {
        // Unconditional invalidate: there is no map-surface path left to skip,
        // so every change needs a rebuild (replaced the `if (!streetNameOnSurface())
        // invalidate()` gating). True when the name changed, false otherwise.
        assertTrue(NavigationScreen.needsEtaCardRebuild(changed = true))
        assertEquals(false, NavigationScreen.needsEtaCardRebuild(changed = false))
    }

    // ── effectiveFixArgs (spec: auto-smooth-follow — fix feed; regression:
    // the routing view used to drop the speed, leaving the renderer's
    // extrapolation gate closed and the map snapping per 1 Hz fix) ──

    @Test
    fun effectiveFixArgsPassesGpsSpeedToRenderer() {
        val args = NavigationScreen.effectiveFixArgs(
            AutoPosition(lat = 51.0, lon = 7.0, bearing = 90.0, speedKmH = 60.0),
            nowMs = 5_000L,
            derivation = AutoFixDerivation()
        )
        assertEquals(60.0, args.speedKmH, 1e-9)
        assertEquals(90.0, args.bearing, 1e-9)
        assertEquals(51.0, args.lat, 1e-9)
        assertEquals(5_000L, args.timeMs)
    }

    @Test
    fun effectiveFixArgsDerivesSpeedWhenGpsSpeedMissing() {
        // Second fix moves 0.1° north over 600 s → ≈ 66.7 km/h derived; the
        // renderer must receive it (not the NaN default) so smooth scrolling
        // runs on receivers without a GPS speed.
        val d = AutoFixDerivation()
        NavigationScreen.effectiveFixArgs(
            AutoPosition(lat = 51.0, lon = 7.0, speedKmH = Double.NaN), nowMs = 0L, derivation = d
        )
        val args = NavigationScreen.effectiveFixArgs(
            AutoPosition(lat = 51.1, lon = 7.0, speedKmH = Double.NaN), nowMs = 600_000L, derivation = d
        )
        assertEquals(66.7, args.speedKmH, 1.0)
    }

    @Test
    fun effectiveFixArgsDerivesBearingWhenGpsBearingMissing() {
        val d = AutoFixDerivation()
        NavigationScreen.effectiveFixArgs(
            AutoPosition(lat = 51.0, lon = 7.0, bearing = Double.NaN), nowMs = 0L, derivation = d
        )
        val args = NavigationScreen.effectiveFixArgs(
            AutoPosition(lat = 51.1, lon = 7.0, bearing = Double.NaN), nowMs = 600_000L, derivation = d
        )
        assertEquals(0.0, args.bearing, 0.5)
    }
}
