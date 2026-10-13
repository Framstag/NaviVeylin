package com.naviveylin.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Rate-band contract of the follow display: the phone's displayed map position must advance at
 * approximately the vehicle's speed and must never stop dead.
 *
 * Specs: `smooth-follow` — Correction easing (all seven cases below), Follow frame refresh before
 * the overrun margin is exhausted (the effective-speed input these cases feed); `auto-smooth-follow`
 * — Correction easing runs the same primitive (its own cases live in `AutoMapRendererTest`).
 *
 * The driver runs the real [FollowPrediction] and [FollowDisplayState] frame by frame (60 fps) over
 * an injected fix stream — the same stimulus shapes the emulator produced (a steady 50 km/h track,
 * along-track jitter, a speed step, a lost fix). Nothing here asserts on an internal field: the
 * measured quantities (displayed rate, effective speed, position error against the injected truth)
 * are the contract.
 *
 * The overshoot cases model a curve's or a deceleration's *along-track* overshoot (that is the only
 * component the forward-only rule could act on) as a deceleration step, which needs no route model.
 */
class FollowDisplayRateTest {

    private data class Fix(val tSec: Double, val lat: Double, val lon: Double, val speedMs: Double, val headingDeg: Double)

    private data class Frame(val tSec: Double, val rate: Double, val vEff: Double, val errM: Double)

    /**
     * An injected track: due north so the geometry stays exact, one fix per [fixIntervalSec].
     * The receiver reports the *previous* interval's speed (a one-fix lag, as a receiver does), and
     * an odd-fix [speedStepScale] reproduces a speed estimate that steps.
     */
    private class Track(
        val fixIntervalSec: Double,
        val seconds: Double,
        val jitterM: Double = 0.0,
        val reportedScale: Double = 1.0,
        val speedStepScale: Double = 1.0,
        val slowAtSec: Double = Double.POSITIVE_INFINITY,
        val slowV: Double = 0.0,
        val jumpAtFix: Int = -1,
        val jumpM: Double = 0.0,
        val fixesUntilSec: Double = Double.POSITIVE_INFINITY,
        val seed: Long = 42L,
    ) {
        fun trueSpeedAt(tSec: Double): Double = if (tSec < slowAtSec) V else slowV

        fun trueDistanceAt(tSec: Double): Double =
            V * minOf(tSec, slowAtSec) + if (tSec <= slowAtSec) 0.0 else (tSec - slowAtSec) * slowV

        private fun reportedSpeed(k: Int): Double {
            val previous = maxOf(0.0, (k - 1) * fixIntervalSec)
            var s = trueSpeedAt(previous) * reportedScale
            if (k % 2 == 1) s *= speedStepScale
            return s
        }

        fun fixes(): List<Fix> {
            val rnd = Random(seed)
            val out = ArrayList<Fix>()
            var k = 0
            while (k * fixIntervalSec <= minOf(seconds, fixesUntilSec)) {
                val tSec = k * fixIntervalSec
                var d = trueDistanceAt(tSec)
                if (jitterM > 0.0) d += (rnd.nextDouble() * 2.0 - 1.0) * jitterM
                if (k == jumpAtFix) d += jumpM
                out += Fix(tSec, LAT0 + d / METERS_PER_DEG_LAT, LON0, reportedSpeed(k), 0.0)
                k++
            }
            return out
        }
    }

    /**
     * The single seam the fix touches: one display step. The primitive takes the committed effective
     * speed and the age of the fix it corrects toward, and references the FIX (not the extrapolated
     * position). The case stimuli and assertions below are unchanged from the red-on-HEAD run — only
     * this call adapts to the new API.
     */
    private fun step(
        display: FollowDisplayState,
        fixLat: Double,
        fixLon: Double,
        dtSec: Double,
        headingDeg: Double,
        vEff: Double,
        fixAgeSec: Double
    ): Pair<Double, Double> = display.advance(fixLat, fixLon, dtSec, headingDeg, vEff, fixAgeSec)

    private fun run(track: Track): List<Frame> {
        val prediction = FollowPrediction()
        val display = FollowDisplayState()
        val fixes = track.fixes()
        val frames = ArrayList<Frame>()
        var next = 0
        var trueDist = 0.0
        var prevLat = Double.NaN
        var prevLon = Double.NaN
        var baseLat = Double.NaN
        var baseLon = Double.NaN
        var baseSec = 0.0
        var f = 0
        val total = (track.seconds / DT).toInt()
        while (f <= total) {
            val tSec = f * DT
            val nowMs = (tSec * 1000.0).toLong()
            trueDist += track.trueSpeedAt(tSec) * DT
            while (next < fixes.size && fixes[next].tSec <= tSec) {
                val fx = fixes[next]
                next++
                prediction.update(fx.lat, fx.lon, fx.speedMs, fx.headingDeg, nowMs)
                baseLat = fx.lat
                baseLon = fx.lon
                baseSec = tSec
            }
            // The reference is the extrapolated target (a continuous reference); the fix's own age
            // gates the correction and the snap (design D4).
            val vEff = prediction.effectiveSpeedMs(nowMs)
            val target = prediction.predictedPosition(nowMs)
            val (lat, lon) = step(display, target.first, target.second, DT, 0.0, vEff, tSec - baseSec)
            if (!prevLat.isNaN()) {
                frames += Frame(
                    tSec,
                    meters(prevLat, prevLon, lat, lon) / DT,
                    vEff,
                    meters(lat, lon, LAT0 + trueDist / METERS_PER_DEG_LAT, LON0)
                )
            }
            prevLat = lat
            prevLon = lon
            f++
        }
        return frames
    }

    // ---------------------------------------------------------------- assertions

    /** The displayed rate must track the effective speed within the design's ±30 % band. */
    private fun assertTracksEffective(frames: List<Frame>, label: String) {
        val considered = frames.filter { it.tSec >= WARMUP_SEC }
        val bad = considered.filter { it.vEff > 0.5 && (it.rate < 0.70 * it.vEff || it.rate > 1.30 * it.vEff) }
        val first = bad.firstOrNull()
        assertTrue(
            "$label: ${bad.size} of ${considered.size} frames outside 0.70..1.30 x vEff" +
                (first?.let { "; first at t=${it.tSec}s rate=${it.rate} vEff=${it.vEff}" } ?: ""),
            bad.isEmpty()
        )
    }

    /** The displayed rate must stay close to the vehicle's true speed — the user-visible band. */
    private fun assertVehicleBand(frames: List<Frame>, label: String) {
        val considered = frames.filter { it.tSec >= WARMUP_SEC }
        val bad = considered.filter { it.rate < 0.6 * V || it.rate > 1.4 * V }
        val first = bad.firstOrNull()
        assertTrue(
            "$label: ${bad.size} of ${considered.size} frames outside 0.6..1.4 x ${V} m/s" +
                (first?.let { "; first at t=${it.tSec}s rate=${it.rate}" } ?: ""),
            bad.isEmpty()
        )
    }

    /** No halt: no run of frames longer than [HALT_LIMIT_SEC] below a quarter of the vehicle speed. */
    private fun assertNoHalt(frames: List<Frame>, label: String) {
        val limit = 0.25 * V
        var runStart = Double.NaN
        var worst = 0.0
        var worstAt = 0.0
        for (fr in frames) {
            if (fr.tSec < WARMUP_SEC) continue
            if (fr.rate < limit) {
                if (runStart.isNaN()) runStart = fr.tSec
                val len = fr.tSec - runStart + DT
                if (len > worst) {
                    worst = len
                    worstAt = runStart
                }
            } else {
                runStart = Double.NaN
            }
        }
        assertTrue("$label: longest halt ${worst}s at t=${worstAt}s (limit $HALT_LIMIT_SEC s)", worst <= HALT_LIMIT_SEC)
    }

    /** The effective speed is the display's input: it may not step per fix (design D3). */
    private fun assertEffectiveSpeedStep(frames: List<Frame>, label: String) {
        var worst = 0.0
        var worstAt = 0.0
        for (i in 1 until frames.size) {
            val jump = Math.abs(frames[i].vEff - frames[i - 1].vEff)
            if (jump > worst) {
                worst = jump
                worstAt = frames[i].tSec
            }
        }
        val allowed = SPEED_STEP_MS2 * DT * 1.5
        assertTrue("$label: effective speed stepped ${worst} m/s in one frame at t=${worstAt}s (allowed $allowed)", worst <= allowed)
    }

    private fun assertErrorBounded(frames: List<Frame>, maxM: Double, label: String) {
        val considered = frames.filter { it.tSec >= WARMUP_SEC }
        val worst = considered.maxOfOrNull { it.errM } ?: 0.0
        assertTrue("$label: position error reached ${worst} m (limit $maxM m)", worst <= maxM)
    }

    // ---------------------------------------------------------------- cases

    @Test
    fun `cornerOvershootStaysInBand`() {
        // ~7 m of along-track overshoot at fix arrival: the vehicle drops to 10 m/s while the
        // reported speed still carries the previous interval.
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 14.0, slowAtSec = 4.0, slowV = 10.0))
        assertTracksEffective(frames, "corner (overshoot) overshoot")
        assertNoHalt(frames, "corner (overshoot)")
    }

    @Test
    fun `backwardFixJumpKeepsRateInBand`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 12.0, jumpAtFix = 6, jumpM = -8.0))
        assertTracksEffective(frames, "backward fix jump")
        assertVehicleBand(frames, "backward fix jump")
        assertNoHalt(frames, "backward fix jump")
    }

    @Test
    fun `subPixelCorrectionIsImperceptible`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 10.0, jumpAtFix = 5, jumpM = 2.0))
        assertTracksEffective(frames, "2 m correction")
    }

    @Test
    fun `forwardGapNeverExceedsBound`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 10.0, jumpAtFix = 5, jumpM = 8.0))
        assertTracksEffective(frames, "8 m forward gap")
        assertVehicleBand(frames, "8 m forward gap")
    }

    @Test
    fun `speedBiasLeavesBoundedError`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 14.0, reportedScale = 0.95))
        assertErrorBounded(frames, 1.0, "5 % speed bias")
    }

    @Test
    fun `lostFixKeepsForwardMotion`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 9.0, fixesUntilSec = 4.0))
        assertTracksEffective(frames, "lost fix")
        assertNoHalt(frames, "lost fix")
    }

    @Test
    fun `jitteredStreamHasNoHalt`() {
        val frames = run(Track(fixIntervalSec = 1.0, seconds = 14.0, jitterM = 3.0))
        assertTracksEffective(frames, "3 m jitter")
        assertNoHalt(frames, "3 m jitter")
    }

    @Test
    fun `parameterSweepStaysInBand`() {
        for (cadence in listOf(0.5, 1.0, 2.0)) {
            for (jitter in listOf(1.0, 3.0, 8.0)) {
                for (step in listOf(1.0, 0.85, 0.7)) {
                    val label = "sweep cadence=${cadence}s jitter=${jitter}m speedStep=$step"
                    val frames = run(
                        Track(
                            fixIntervalSec = cadence,
                            seconds = 3 * cadence + 8.0,
                            jitterM = jitter,
                            speedStepScale = step
                        )
                    )
                    assertNoHalt(frames, label)
                    assertEffectiveSpeedStep(frames, label)
                    // The vehicle band only applies where the vehicle's speed is the stimulus: the
                    // reported-speed-step variants are a sensor fiction (the truth is constant), and
                    // the deceleration cap is allowed to slow the display there.
                    if (step == 1.0) assertVehicleBand(frames, label)
                }
            }
        }
    }

    private companion object {
        /** 50 km/h, the speed the emulator reproduction used. */
        const val V = 13.889
        const val DT = 1.0 / 60.0
        const val WARMUP_SEC = 2.5
        const val HALT_LIMIT_SEC = 0.15

        /** The bound design D3 puts on the effective speed (m/s per second). */
        const val SPEED_STEP_MS2 = 2.5

        const val LAT0 = 53.0793
        const val LON0 = 8.8017
        const val METERS_PER_DEG_LAT = 111320.0

        fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = (lat2 - lat1) * METERS_PER_DEG_LAT
            val dLon = (lon2 - lon1) * METERS_PER_DEG_LAT * Math.cos(Math.toRadians(lat1))
            return Math.sqrt(dLat * dLat + dLon * dLon)
        }
    }
}
