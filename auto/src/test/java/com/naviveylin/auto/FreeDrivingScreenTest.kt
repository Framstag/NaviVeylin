package com.naviveylin.auto

import com.framstag.libosmscout.client.FakeAutoRenderClient
import com.naviveylin.core.AutoPositionUtil
import com.naviveylin.core.VehicleAnchorPosition
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [FreeDrivingScreen.headingAngleRadians] — the heading-up rotation
 * decision (spec: auto/free-driving — "Heading-up orientation"). The screen
 * itself needs a live CarContext + Hilt entry point (same constraint as
 * [MapScreenTest]); the rotation decision is a pure companion function so it
 * can be pinned here.
 */
@RunWith(RobolectricTestRunner::class)
class FreeDrivingScreenTest {

    /** Shuts a spied renderer down after the test: the spy runs the real renderer,
     *  whose loops start in `init` and would otherwise outlive the test
     *  (change `fix-auto-unit-test-heap-overflow`, `TODO.md` §33). */
    @get:Rule
    val renderers = RendererTestRule()

    @Test
    fun bearingRotatesHeadingUp() {
        // 90° east → -π/2 radians: the projections rotate by R(-angle), so a
        // negative angle makes the driving direction point up (same
        // convention as the phone app).
        val angle = FreeDrivingScreen.headingAngleRadians(90.0)
        assertTrue(angle != null)
        assertEquals(-Math.PI / 2, angle!!, 1e-9)
    }

    @Test
    fun zeroBearingKeepsNorthUp() {
        assertEquals(0.0, FreeDrivingScreen.headingAngleRadians(0.0)!!, 1e-9)
    }

    @Test
    fun unknownBearingReturnsNull() {
        // NaN/negative bearing: keep the previous heading (spec: "Heading-up
        // independent of settings" — never snaps to north on a bad fix).
        assertNull(FreeDrivingScreen.headingAngleRadians(Double.NaN))
        assertNull(FreeDrivingScreen.headingAngleRadians(-1.0))
    }

    // ── autoZoomTarget (spec: auto/map-pan — auto-zoom suspended while
    // panned; design D2 — same gate as NavigationScreen) ──

    @Test
    fun panningSuppressesAutoZoomFeed() {
        // A suspended controller re-engages on a speed-band crossing
        // (highway speed fed from a city band) — the panning gate must
        // return null regardless, mirroring NavigationScreen.
        val controller = AutoZoomController()
        controller.onSpeed(45.0) // city band
        controller.suspend()
        assertNull(
            FreeDrivingScreen.autoZoomTarget(
                panning = true, autoZoomEnabled = true, speedKmH = 100.0, controller = controller
            )
        )
    }

    // ── autoZoomOnReEnable (spec: auto-speed-zoom — Auto-zoom re-enabled after a
    // manual zoom; design D5) ──

    @Test
    fun reEnablingAutoZoomAppliesTheTargetFromTheLastKnownSpeed() {
        // The re-enable path has no fix in hand, so it must use the last known speed —
        // otherwise the adjustment would wait for the next fix (up to ~1 s) and the
        // scenario "begins without waiting for the next GPS fix" would not hold.
        val controller = AutoZoomController()
        val target = FreeDrivingScreen.autoZoomOnReEnable(
            wasEnabled = false, enabled = true, speedKmH = 30.0, panning = false, controller = controller
        )
        assertEquals("city speed 30 km/h -> magnification 16.0", 16.0, target ?: Double.NaN, 1e-9)
    }

    @Test
    fun reEnableIsANoOpWhenTheSettingDidNotFlipOn() {
        val controller = AutoZoomController()
        assertNull(
            "an already-enabled setting must not re-commit the zoom",
            FreeDrivingScreen.autoZoomOnReEnable(
                wasEnabled = true, enabled = true, speedKmH = 30.0, panning = false, controller = controller
            )
        )
        assertNull(
            "and disabling must not either",
            FreeDrivingScreen.autoZoomOnReEnable(
                wasEnabled = true, enabled = false, speedKmH = 30.0, panning = false, controller = controller
            )
        )
    }

    @Test
    fun reEnableWaitsWithoutAUsableSpeed() {
        val controller = AutoZoomController()
        assertEquals(
            "no speed seen yet: the controller's seeded 20 km/h default still gives a target, " +
                "so the re-enable does not wait for the first fix (task 7.3)",
            16.0,
            FreeDrivingScreen.autoZoomOnReEnable(
                wasEnabled = false, enabled = true, speedKmH = Double.NaN, panning = false, controller = controller
            ) ?: Double.NaN,
            1e-9
        )
    }

    @Test
    fun reEnableKeepsThePanGate() {
        val controller = AutoZoomController()
        assertNull(
            "a panned map must not be yanked back by a re-enable",
            FreeDrivingScreen.autoZoomOnReEnable(
                wasEnabled = false, enabled = true, speedKmH = 30.0, panning = true, controller = controller
            )
        )
    }

    // ── commitAutoZoom (spec: auto-speed-zoom — Auto-zoom entry transition / Auto-zoom
    // re-enabled after a manual zoom; design D1/D2, D5) — the commit body both car screens
    // share for their re-enable/entry paths ──

    @Test
    fun commitAutoZoomIsTransitionEligibleAndReengagesFollow() {
        // The commit must carry the transition-eligible flag (otherwise the entry lands the whole
        // difference in one frame again) and must re-engage follow without snapping, because
        // `setViewport` disengages it.
        val gate = RendererGate()
        val renderer = renderers.track(spyk(AutoMapRenderer(FakeAutoRenderClient(), initialProjectionDpi = 240.0)))
        gate.publish(renderer)

        val applied = FreeDrivingScreen.commitAutoZoom(gate, 16.0, "test")

        assertTrue("the commit is applied", applied)
        verify { renderer.setViewport(any(), any(), 16, any(), 16.0, true) }
        verify { renderer.reengageFollow() }

        // The spy runs the real renderer, whose three background loops would otherwise
        // outlive the test (small unit-test heap).
        gate.destroy()
    }

    @Test
    fun commitAutoZoomIsANoOpBeforeTheRendererIsPublished() {
        // The renderer is built off the car-app main thread; a re-enable that arrives first must
        // not commit anything (and must not throw).
        val gate = RendererGate()

        assertFalse(
            "no renderer yet -> nothing is committed",
            FreeDrivingScreen.commitAutoZoom(gate, 16.0, "test")
        )
    }

    // ── autoZoomTarget with an unknown speed (spec: auto-speed-zoom — Speed unknown) ──

    @Test
    fun unknownSpeedUsesTheSeededDefault() {
        // The car surface MUST reach a "reasonable initial zoom" before the first reported speed,
        // exactly as the phone does through its own seed (`MapCanvasViewModel.lastValidSpeedKmH =
        // 20.0`): a negative (unknown) speed resolves to the seeded default, 20 km/h -> 16.0, and
        // that target is then WALKED from the displayed magnification by the renderer's entry
        // transition (spec: auto-speed-zoom — Speed unknown / Speed unknown while a magnification
        // is displayed; change tasks 7.1-7.3).
        val controller = AutoZoomController()

        assertEquals(
            "20 km/h (the spec's default speed) is the slow-city level 16.0",
            16.0,
            FreeDrivingScreen.autoZoomTarget(
                panning = false, autoZoomEnabled = true, speedKmH = -1.0, controller = controller
            ) ?: Double.NaN,
            1e-9
        )
        assertNull("and a constant unknown speed never re-commits", controller.onSpeed(-1.0))
    }

    // ── anchorDiff (spec: auto/free-driving — anchor applies live during
    // the session) ──

    @Test
    fun anchorDiffReturnsNewAnchorWhenChanged() {
        assertEquals(
            VehicleAnchorPosition.BOTTOM_RIGHT,
            FreeDrivingScreen.anchorDiff(VehicleAnchorPosition.DEFAULT, VehicleAnchorPosition.BOTTOM_RIGHT.id)
        )
    }

    @Test
    fun anchorDiffIsNoOpWhenUnchanged() {
        assertNull(FreeDrivingScreen.anchorDiff(VehicleAnchorPosition.DEFAULT, VehicleAnchorPosition.DEFAULT.id))
    }

    @Test
    fun anchorDiffIsNoOpForUnresolvableId() {
        // An unknown id resolves to the default — the same no-op shape as a
        // failed re-read, which mutates nothing (spec: "Free-driving anchor
        // survives a settings re-read failure").
        assertNull(FreeDrivingScreen.anchorDiff(VehicleAnchorPosition.DEFAULT, "not-an-anchor"))
    }

    // ── Street-pill placement row rule (design D2/D4, spec:
    // auto/free-driving — same rows as the browse and phone labels) ──

    @Test
    fun bottomRowAnchorPlacesLabelAtTop() {
        // Bottom-row presets (fy == 0.9) move the pill to the top edge so it
        // never sits between the vehicle and the way ahead.
        assertEquals(
            StreetNameLabel.Placement.TOP,
            StreetNameLabel.placementFor(VehicleAnchorPosition.BOTTOM_CENTER)
        )
        assertEquals(
            StreetNameLabel.Placement.TOP,
            StreetNameLabel.placementFor(VehicleAnchorPosition.BOTTOM_RIGHT)
        )
        assertEquals(
            StreetNameLabel.Placement.TOP,
            StreetNameLabel.placementFor(VehicleAnchorPosition.BOTTOM_FAR_LEFT)
        )
    }

    @Test
    fun topAndMiddleRowAnchorsPlaceLabelAtBottom() {
        // Top-row and middle-row presets keep the bottom placement.
        assertEquals(
            StreetNameLabel.Placement.BOTTOM,
            StreetNameLabel.placementFor(VehicleAnchorPosition.TOP_CENTER)
        )
        assertEquals(
            StreetNameLabel.Placement.BOTTOM,
            StreetNameLabel.placementFor(VehicleAnchorPosition.CENTER)
        )
        assertEquals(
            StreetNameLabel.Placement.BOTTOM,
            StreetNameLabel.placementFor(VehicleAnchorPosition.MIDDLE_LEFT)
        )
    }
}
