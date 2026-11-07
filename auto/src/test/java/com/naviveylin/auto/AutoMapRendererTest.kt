package com.naviveylin.auto

import io.mockk.every
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [AutoMapRenderer]'s prediction band: the extrapolation gate, the displayed position between fixes,
 * and the correction bound the smooth-follow scenarios name.
 *
 * This file keeps the class name because six shipped scenarios of `auto-smooth-follow` name their
 * case as `AutoMapRendererTest.<case>` — `cornerOvershootStaysInBand`, `subPixelCorrectionIsImperceptible`,
 * `backwardFixDoesNotHaltCarDisplay`, `curveSequenceStaysInBand`,
 * `sharedCorrectionBoundAppliesToCar`, `staleBaseDoesNotStopCarDisplay` — and the scenario of
 * `ci-unit-test-jni` names the class itself. The sibling classes hold the other groups; together
 * they were one 2 099-line file until it crossed the `source-size` row's bound (item #300).
 */
@RunWith(RobolectricTestRunner::class)
class AutoMapRendererTest : AutoMapRendererTestSupport() {

    @Test
    fun extrapolationGateStopsWhenStationaryPausedOrDisengaged() {
        // Task 3.1: the loop runs only when resumed + follow mode + moving +
        // fresh fix + valid surface.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        val now = System.currentTimeMillis()

        // No fix yet -> not moving -> gate off.
        assertFalse(renderer.extrapolationGateActive(now))

        // An UNKNOWN speed (the derivation's sentinel, as reported when no speed is known and the
        // vehicle has not moved) keeps the gate CLOSED: the auto-zoom default-speed seed (20 km/h)
        // is a target-computation default and must never reach this feed, or a parked car would
        // glide as if it moved (spec: auto-smooth-follow — "Fix feed from follow-mode screens";
        // change task 7.2).
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = -1.0, timeMs = now)
        assertFalse("an unknown speed is not the 20 km/h seed", renderer.extrapolationGateActive(now + 100))

        // Moving fix -> gate on.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        assertTrue(renderer.extrapolationGateActive(now + 100))

        // Vehicle stops -> gate off.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 0.0, timeMs = now + 1000)
        assertFalse(renderer.extrapolationGateActive(now + 1100))

        // Moving again, then paused -> gate off.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now + 2000)
        renderer.pause()
        assertFalse(renderer.extrapolationGateActive(now + 2100))
        renderer.resume()
        assertTrue(renderer.extrapolationGateActive(now + 2200))

        // Follow disengaged (pan) -> gate off.
        renderer.setViewport(51.5142273, 7.4652789, 12, 0.0)
        assertFalse(renderer.extrapolationGateActive(now + 2300))
    }

    @Test
    fun markerRidesPredictedPositionBetweenFixes() {
        // Task 3.3: the GPS marker is drawn at the predicted (displayed)
        // position each frame so it glides with the blitted map.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        // Before the first tick the display is not initialized -> raw fix.
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)

        renderer.extrapolationTick(now + 100, 0.1)
        val (mlat, mlon) = renderer.markerPosition()
        // The marker moved off the raw fix along the prediction.
        assertTrue(mlat != 51.5142273 || mlon != 7.4652789)
    }

    @Test
    fun staleFixHoldsPositionInsteadOfExtrapolating() {
        // Task 4.1/4.2: the prediction uses the fix time — a stale fix holds
        // the position (display-only; the nav engine never sees it).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(
            51.5142273, 7.4652789, 45.0, 10.0,
            speedKmH = 10.0, timeMs = now - 10_000
        )
        renderer.extrapolationTick(now, 0.1)

        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        assertEquals(7.4652789, renderer.markerPosition().second, 1e-9)
    }

    @Test
    fun staleFixHoldsDisplayInsteadOfEasingBack() {
        // Delta fix-aa-follow-vehicle-jumps: forward-only displayed position —
        // with a stale fix the prediction holds at the fix and the display
        // HOLDS ahead of it (never slides backward along the direction of
        // travel). The pre-delta behavior eased the display back toward the
        // stale fix — a backward correction slide, the exact jerk the
        // forward-only rule forbids. The held lead is bounded by the eased
        // lead (≤ v·tau); the next moving fix's prediction advances past it.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 10.0, timeMs = now)
        // Let the display run ahead of the fix (fresh fix, extrapolating).
        renderer.extrapolationTick(now + 500, 0.1)
        val ahead = renderer.markerPosition()
        assertTrue(abs(ahead.first - 51.5142273) > 1e-7)

        // Now the fix is stale (10 s old): the prediction holds at the fix.
        // The display must NOT ease back — it holds exactly at its position.
        renderer.extrapolationTick(now + 10_000, 0.1)
        val after = renderer.markerPosition()
        assertEquals(ahead.first, after.first, 0.0)
        assertEquals(ahead.second, after.second, 0.0)
        // And it stays ahead of the (stale) fix, bounded by the eased lead.
        assertTrue(abs(after.first - 51.5142273) >= 1e-7)
    }

    @Test
    fun backwardFixDoesNotHaltCarDisplay() {
        // Spec (modified): auto-smooth-follow — Correction easing, "Fix arrival behind the display
        // holds". The behaviour under that name changed with the bounded-rate display: the display no
        // longer HOLDS when the target drops behind — it slows to no less than (1 - CORR_FRACTION) of
        // the effective speed and keeps advancing east.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        // Eastbound at 14 m/s (50.4 km/h).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)
        val (_, lonAhead) = renderer.markerPosition()
        // The display advanced east of the fix.
        assertTrue(lonAhead > 7.4652789)

        // Fix arrives with the vehicle having barely advanced (deceleration): the target line now
        // lies BEHIND the displayed position. The display must keep advancing — slower, never held,
        // never reversed (the emulator reproduction of 2026-10-12 measured a full stop here).
        renderer.setGpsMarker(51.5142273, 7.4652789 + 1e-6, 90.0, 10.0, speedKmH = 50.4, timeMs = now + 300)
        renderer.extrapolationTick(now + 305, 0.005)
        val lonAfter = renderer.markerPosition().second
        val stepDeg = lonAfter - lonAhead
        assertTrue("display must not stop or reverse", stepDeg > 0.0)
        val freeStepDeg = 14.0 * 0.005 / (111320.0 * kotlin.math.cos(Math.toRadians(51.5142273)))
        assertTrue("advance $stepDeg deg exceeds the free advance $freeStepDeg", stepDeg < freeStepDeg)

        // The display keeps advancing with the following ticks.
        renderer.extrapolationTick(now + 800, 0.2)
        assertTrue(renderer.markerPosition().second > lonAfter)
    }

    /** Eastward metres between two displayed positions (the renderer's local-metre convention). */
    private fun eastMeters(a: Pair<Double, Double>, b: Pair<Double, Double>): Double =
        (b.second - a.second) * 111320.0 * kotlin.math.cos(Math.toRadians(a.first))

    /** Full distance between two displayed positions — the measure a turned heading needs. */
    private fun metersBetween(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val north = (b.first - a.first) * 111320.0
        val east = (b.second - a.second) * 111320.0 * kotlin.math.cos(Math.toRadians(a.first))
        return kotlin.math.sqrt(north * north + east * east)
    }

    @Test
    fun cornerOvershootStaysInBand() {
        // Spec: auto-smooth-follow — Correction easing, "Prediction drifts before fix arrival". The
        // fix lands 8 m behind the extrapolation (a curve / a deceleration): every displayed step
        // stays inside the bound — never held, never reversed, never faster than the bound.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        val vMs = 14.0
        val lat = 51.5142273
        renderer.setGpsMarker(lat, 7.4652789, 90.0, 10.0, speedKmH = vMs * 3.6, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)

        // The overshooting fix: the vehicle barely advanced while the prediction ran on.
        val behindLon = 7.4652789 - 8.0 / (111320.0 * kotlin.math.cos(Math.toRadians(lat)))
        renderer.setGpsMarker(lat, behindLon, 90.0, 10.0, speedKmH = vMs * 3.6, timeMs = now + 200)
        var prev = renderer.markerPosition()
        var t = now + 200
        repeat(20) { i ->
            t += 50
            renderer.extrapolationTick(t, 0.05)
            val cur = renderer.markerPosition()
            val stepM = eastMeters(prev, cur)
            assertTrue("tick $i step $stepM m below the bound", stepM >= 0.70 * vMs * 0.05 - 1e-6)
            assertTrue("tick $i step $stepM m above the bound", stepM <= 1.30 * vMs * 0.05 + 1e-6)
            prev = cur
        }
    }

    @Test
    fun subPixelCorrectionIsImperceptible() {
        // Spec: auto-smooth-follow — Correction easing, "Fix arrives close to prediction". A 2 m
        // correction must not move the displayed rate outside the band.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        val vMs = 14.0
        val lat = 51.5142273
        renderer.setGpsMarker(lat, 7.4652789, 90.0, 10.0, speedKmH = vMs * 3.6, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)

        val aheadLon = 7.4652789 + 2.0 / (111320.0 * kotlin.math.cos(Math.toRadians(lat)))
        renderer.setGpsMarker(lat, aheadLon, 90.0, 10.0, speedKmH = vMs * 3.6, timeMs = now + 200)
        var prev = renderer.markerPosition()
        var t = now + 200
        repeat(10) { i ->
            t += 100
            renderer.extrapolationTick(t, 0.1)
            val cur = renderer.markerPosition()
            val stepM = eastMeters(prev, cur)
            assertTrue("tick $i step $stepM m below the bound", stepM >= 0.70 * vMs * 0.1 - 1e-6)
            assertTrue("tick $i step $stepM m above the bound", stepM <= 1.30 * vMs * 0.1 + 1e-6)
            prev = cur
        }
    }

    @Test
    fun curveSequenceStaysInBand() {
        // Spec: auto-smooth-follow — Correction easing, "Forward-only across consecutive fixes". A
        // series of curve fixes: the displayed rate stays inside the band on every tick and never
        // reverses.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        val vMs = 14.0
        var bearing = 96.0
        var lat = 51.5142273
        var lon = 7.4652789
        var t = now
        renderer.setGpsMarker(lat, lon, bearing, 10.0, speedKmH = vMs * 3.6, timeMs = t)
        // Warm-up tick: the first advance initialises the display to the target, which is not a
        // displayed step.
        renderer.extrapolationTick(t + 200, 0.2)
        t += 200
        var prev = renderer.markerPosition()
        repeat(5) { f ->
            bearing += 6.0
            lat += 14.0 * kotlin.math.cos(Math.toRadians(bearing)) / 111320.0
            lon += 14.0 * kotlin.math.sin(Math.toRadians(bearing)) /
                (111320.0 * kotlin.math.cos(Math.toRadians(lat)))
            t += 1_000
            renderer.setGpsMarker(lat, lon, bearing, 10.0, speedKmH = vMs * 3.6, timeMs = t)
            repeat(4) { i ->
                t += 250
                renderer.extrapolationTick(t, 0.25)
                val cur = renderer.markerPosition()
                val stepM = metersBetween(prev, cur)
                assertTrue("fix $f tick $i step $stepM m below the bound", stepM >= 0.70 * vMs * 0.25 - 1e-6)
                assertTrue("fix $f tick $i step $stepM m above the bound", stepM <= 1.30 * vMs * 0.25 + 1e-6)
                prev = cur
            }
        }
    }

    @Test
    fun sharedCorrectionBoundAppliesToCar() {
        // Spec: auto-smooth-follow — Shared follow bounds on the car surface. Straight driving on a
        // single fix: every tick's advance stays inside the bound on both sides, so the car needs no
        // bound of its own.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        val vMs = 14.0
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = vMs * 3.6, timeMs = now)
        var prev = renderer.markerPosition()
        var t = now
        repeat(30) { i ->
            t += 50
            renderer.extrapolationTick(t, 0.05)
            val cur = renderer.markerPosition()
            val stepM = eastMeters(prev, cur)
            assertTrue("tick $i step $stepM m below the bound", stepM >= 0.70 * vMs * 0.05 - 1e-6)
            assertTrue("tick $i step $stepM m above the bound", stepM <= 1.30 * vMs * 0.05 + 1e-6)
            prev = cur
        }
    }

    @Test
    fun staleBaseDoesNotStopCarDisplay() {
        // Spec: auto-smooth-follow — Shared follow bounds, "The car inherits the stop-decision
        // guard". A 4 s old fix is a lost feed, not a stationary vehicle: the display must keep
        // advancing (the pre-fix code held it dead still and the reproduction measured a 2.0 s
        // freeze of exactly this shape).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)
        val before = renderer.markerPosition()

        renderer.extrapolationTick(now + 4_200, 0.2)
        val after = renderer.markerPosition()
        assertTrue(
            "a stale feed must not stop the car display (moved ${eastMeters(before, after)} m)",
            eastMeters(before, after) > 0.0
        )
    }

    @Test
    fun gateFreezeKeepsDisplayAndMarkerAtStop() {
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Vehicle stops"/"Vehicle resumes after a stop"): the stopped gate
        // freezes the displayed position and marker — no NaN reset, no
        // raw-fix fallback, no ease back toward the fix; the resume continues
        // from the frozen position.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now)
        renderer.extrapolationTick(now + 200, 0.2)
        val (frozenLat, frozenLon) = renderer.markerPosition()
        assertTrue(frozenLon > 7.4652789)

        // Stop (same fix, speed 0): the gate closes; a stationary tick must
        // not mutate the display. The marker stays on the frozen displayed
        // position (east of the fix), NOT the raw fix (lon 7.4652789).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 0.0, timeMs = now + 1000)
        renderer.extrapolationTick(now + 1_100, 0.1)
        assertEquals(frozenLat, renderer.markerPosition().first, 0.0)
        assertEquals(frozenLon, renderer.markerPosition().second, 0.0)
        assertTrue(renderer.markerPosition().second > 7.4652789)

        // Resume: the display advances from the frozen position (no snap).
        renderer.setGpsMarker(51.5142273, 7.4652789, 90.0, 10.0, speedKmH = 50.4, timeMs = now + 2000)
        renderer.extrapolationTick(now + 2_300, 0.3)
        assertTrue(renderer.markerPosition().second > frozenLon)
    }

    @Test
    fun stationaryJitterFixesDoNotReAnchorViewport() {
        // Delta fix-aa-follow-vehicle-jumps (spec: auto-smooth-follow —
        // "Vehicle stops"): the stationary branch seeds the display + viewport
        // to the fix ONCE per stop episode; GPS-jitter fixes after that leave
        // the viewport and the displayed position untouched.
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        // First stationary fix (no previous fix): seed once.
        renderer.setGpsMarker(51.5142273, 7.4652789, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis())
        val vpAfterSeed = renderer.viewportState.value
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
        // Center anchor: the seeded viewport center IS the fix.
        assertEquals(51.5142273, vpAfterSeed.lat, 1e-9)
        assertEquals(7.4652789, vpAfterSeed.lon, 1e-9)

        // Jitter fixes while stopped: nothing re-anchors, the display stays on
        // the seeded fix.
        renderer.setGpsMarker(51.5142273 + 1e-6, 7.4652789 + 1e-6, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis() + 500)
        assertEquals(vpAfterSeed.lat, renderer.viewportState.value.lat, 0.0)
        assertEquals(vpAfterSeed.lon, renderer.viewportState.value.lon, 0.0)
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)

        renderer.setGpsMarker(51.5142273 - 2e-6, 7.4652789 - 1e-6, 45.0, 10.0, speedKmH = 0.0, timeMs = System.currentTimeMillis() + 1000)
        assertEquals(vpAfterSeed.lat, renderer.viewportState.value.lat, 0.0)
        assertEquals(vpAfterSeed.lon, renderer.viewportState.value.lon, 0.0)
        assertEquals(51.5142273, renderer.markerPosition().first, 1e-9)
    }

    @Test
    fun marginRenderRequestHonoursThrottleParity() {
        // Delta fix-aa-follow-vehicle-jumps (design D3): the margin full-render
        // request is throttled at RENDER_REQUEST_INTERVAL_MS = 200 ms (phone
        // parity). A clamped tick inside the window does not re-request (the
        // viewport stays put); one past the boundary does (the viewport
        // re-anchors on the advanced display).
        val (surface, _) = mockSurface()
        renderer.asyncLoopsEnabled = false
        renderer.onSurfaceCreated(surface, 100, 100)
        renderer.renderFrame()

        val now = System.currentTimeMillis()
        // Jump ~1.4 km east + moving at 100 km/h: at zoom 12 that is ~36 px —
        // beyond the overrun margin (10 px), so the display clamps on the
        // first tick and requests the margin render (t0).
        renderer.setGpsMarker(51.5142273, 7.4852, 90.0, 10.0, speedKmH = 100.0, timeMs = now)
        renderer.extrapolationTick(now + 50, 0.05)
        val vp1 = renderer.viewportState.value

        // Inside the 200 ms window: no new request -> viewport unchanged.
        renderer.extrapolationTick(now + 190, 0.14)
        assertEquals(vp1.lat, renderer.viewportState.value.lat, 1e-12)
        assertEquals(vp1.lon, renderer.viewportState.value.lon, 1e-12)

        // Past the boundary: the request fires -> the viewport re-anchors on
        // the (advanced) display.
        renderer.extrapolationTick(now + 350, 0.16)
        val vp3 = renderer.viewportState.value
        assertTrue(
            "vp1=" + vp1.lat + "," + vp1.lon +
                " vp3=" + vp3.lat + "," + vp3.lon +
                " marker=" + renderer.markerPosition(),
            vp1.lat != vp3.lat || vp1.lon != vp3.lon
        )
    }
}
