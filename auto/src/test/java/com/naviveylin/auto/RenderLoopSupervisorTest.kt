package com.naviveylin.auto

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the per-iteration confinement of a continuing car map loop and its re-creation
 * ladder (spec: car-host-fault-isolation — No fault escapes into the host path, "A fault in
 * one frame keeps the frames coming"; A repeatedly faulting car renderer recovers, then
 * degrades visibly; design D1/D2/D7).
 *
 * Default Robolectric sandbox: the default `record` seam reaches `DiagnosticsLog`, which
 * holds an `android.util.Log` reference (per `AGENTS.md`, no `@Config` override here).
 */
@RunWith(RobolectricTestRunner::class)
class RenderLoopSupervisorTest {

    private val records = mutableListOf<String>()
    private var now = 1_000L

    private fun supervisor(
        threshold: Int = CONSECUTIVE_RENDER_FAULT_THRESHOLD,
        windowMs: Long = RENDER_FAULT_WINDOW_MS,
        maxRecoveries: Int = MAX_RENDERER_RECOVERIES_PER_PERIOD
    ) = RenderLoopSupervisor(
        threshold = threshold,
        windowMs = windowMs,
        maxRecoveries = maxRecoveries,
        nowMs = { now },
        record = { records += it }
    )

    private fun RenderLoopSupervisor.boom(loop: RenderLoop): Boolean =
        runIteration(loop) { error("native render failed") } == null

    @Test
    fun aFaultSkipsTheIterationAndTheLoopKeepsRunning() {
        val loops = supervisor()
        val iterations = mutableListOf<String>()

        // A three-iteration loop whose last iteration faults: this is the shape that used
        // to end the loop for good (TODO.md §51) — the frame pipeline stopped while the
        // surface stayed healthy.
        repeat(3) { index ->
            loops.runIteration(RenderLoop.FRAME) {
                if (index == 2) error("native render failed")
                iterations += "frame$index"
            }
        }

        assertEquals(listOf("frame0", "frame1"), iterations)
        assertEquals(1, loops.currentStreak())
    }

    @Test
    fun aSuccessfulIterationResetsTheStreak() {
        val loops = supervisor(threshold = 3)

        loops.boom(RenderLoop.FRAME)
        loops.boom(RenderLoop.FRAME)
        assertEquals(2, loops.currentStreak())

        loops.runIteration(RenderLoop.FRAME) { "frame" }

        // Isolated faults must not accumulate towards a re-creation.
        assertEquals(0, loops.currentStreak())
        assertEquals(0, loops.recoveriesAttempted)
    }

    @Test
    fun theThresholdRequestsExactlyOneRecreationPerStreak() {
        val loops = supervisor(threshold = 3)
        val attempts = mutableListOf<Int>()
        loops.onRecoveryRequested { attempts += it }

        loops.boom(RenderLoop.EXTRAPOLATION)
        loops.boom(RenderLoop.EXTRAPOLATION)
        assertEquals(emptyList<Int>(), attempts)
        loops.boom(RenderLoop.EXTRAPOLATION)

        assertEquals(listOf(1), attempts)
        assertEquals(1, loops.recoveriesAttempted)

        // The next two faults are one short of the threshold again.
        loops.boom(RenderLoop.EXTRAPOLATION)
        loops.boom(RenderLoop.EXTRAPOLATION)
        assertEquals(listOf(1), attempts)
    }

    @Test
    fun theCapStopsRecoveryAndReportstheDegradedStateOnce() {
        val loops = supervisor(threshold = 2, maxRecoveries = 2)
        val attempts = mutableListOf<Int>()
        val degraded = mutableListOf<RenderLoop>()
        loops.onRecoveryRequested { attempts += it }
        loops.onDegraded { loop, _ -> degraded += loop }

        repeat(6) { loops.boom(RenderLoop.ZOOM_WALK) }

        assertEquals(listOf(1, 2), attempts)
        assertEquals(listOf(RenderLoop.ZOOM_WALK), degraded)
        assertTrue(loops.isSuspended)
    }

    @Test
    fun aSuspendedSupervisorAsksForNothing() {
        val loops = supervisor(threshold = 2, maxRecoveries = 2)
        val attempts = mutableListOf<Int>()
        loops.onRecoveryRequested { attempts += it }
        loops.suspend()

        repeat(6) { loops.boom(RenderLoop.FRAME) }

        assertEquals(emptyList<Int>(), attempts)
        assertEquals(0, loops.recoveriesAttempted)
    }

    @Test
    fun aNewPeriodReArmsTheRecoveryBudget() {
        val loops = supervisor(threshold = 2, maxRecoveries = 2)
        val attempts = mutableListOf<Int>()
        loops.onRecoveryRequested { attempts += it }

        repeat(6) { loops.boom(RenderLoop.FRAME) }
        assertEquals(listOf(1, 2), attempts)

        loops.startPeriod()
        assertFalse(loops.isSuspended)
        assertEquals(0, loops.recoveriesAttempted)

        repeat(2) { loops.boom(RenderLoop.FRAME) }
        assertEquals(listOf(1, 2, 1), attempts)
    }

    @Test
    fun aFaultOutsideTheWindowStartsANewStreak() {
        val loops = supervisor(threshold = 3, windowMs = 60_000L)

        loops.boom(RenderLoop.FRAME)
        loops.boom(RenderLoop.FRAME)
        now += RENDER_FAULT_WINDOW_MS + 1
        loops.boom(RenderLoop.FRAME)

        // The third fault arrived outside the window, so the streak restarted at 1 and no
        // re-creation was asked for by a stale streak.
        assertEquals(1, loops.currentStreak())
        assertEquals(0, loops.recoveriesAttempted)
    }

    @Test
    fun cancellationIsRethrownAndCountedAsNoFault() {
        val loops = supervisor()
        var escaped = false

        try {
            loops.runIteration(RenderLoop.FRAME) { throw CancellationException("scope cancelled") }
        } catch (cancelled: CancellationException) {
            escaped = true
        }

        // A swallowed cancellation would leave a loop nothing can stop (renderer shutdown).
        assertTrue(escaped)
        assertEquals(0, loops.currentStreak())
        assertEquals(emptyList<String>(), records)
    }

    @Test
    fun theFaultRecordCarriesIdentityOnlyAndIsThrottled() {
        val loops = supervisor(threshold = 99)

        loops.boom(RenderLoop.ZOOM_WALK)
        assertEquals(1, records.size)
        assertTrue(records[0].contains("zoom-walk"))
        assertTrue(records[0].contains("IllegalStateException"))
        assertTrue(records[0].contains("recoveries=0"))

        // A burst inside the record window adds no further entry.
        loops.boom(RenderLoop.ZOOM_WALK)
        assertEquals(1, records.size)

        now += RENDER_FAULT_RECORD_INTERVAL_MS
        loops.boom(RenderLoop.ZOOM_WALK)
        assertEquals(2, records.size)

        // Identity only: the fault message and any coordinate-shaped text stay out.
        assertFalse(records.any { it.contains("native render failed") })
        assertFalse(records.any { Regex("""\d+\.\d{4,}""").containsMatchIn(it) })
    }

    @Test
    fun reCreationAndDegradedTransitionsAreRecordedWithTheirAttempts() {
        val loops = supervisor(threshold = 2, maxRecoveries = 1)
        loops.onRecoveryRequested { }
        loops.onDegraded { _, _ -> }

        repeat(4) { loops.boom(RenderLoop.FRAME) }

        assertTrue(records.any { it.contains("re-creating after 2 consecutive faults (attempt 1/1)") })
        assertTrue(records.any { it.contains("degraded after 1 re-creation") })
    }

    @Test
    fun aCleanIterationReturnsTheBlocksValue() {
        val loops = supervisor()

        val frame: Int? = loops.runIteration(RenderLoop.FRAME) { 42 }

        assertEquals(42, frame)
        assertNull(loops.runIteration(RenderLoop.FRAME) { error("boom") })
    }
}
