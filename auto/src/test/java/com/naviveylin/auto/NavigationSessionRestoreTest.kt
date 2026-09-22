package com.naviveylin.auto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure-seam tests for the session restore decisions (design D1 — the session
 * root is ALWAYS the browse map; restored modes are pushed on top, never
 * rooted, so the stack stays leaveable when a mode ends). [NavigationSession]
 * needs a host-provided [androidx.car.app.CarContext] and cannot be
 * constructed in Robolectric, so the gate logic is extracted as pure
 * functions (same pattern as `resolveCarDark`/`isNightUiMode`).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationSessionRestoreTest {

    @Test
    fun freeDrivingRestoredWhenActiveAndNotNavigating() {
        assertTrue(shouldRestoreFreeDriving(isNavigating = false, freeDrivingActive = true))
    }

    @Test
    fun freeDrivingNotRestoredWhileNavigating() {
        // Navigation wins: the observer pushes the navigation screen; stacking
        // the free-driving view on top would break the mutual exclusion.
        assertFalse(shouldRestoreFreeDriving(isNavigating = true, freeDrivingActive = true))
    }

    @Test
    fun nothingRestoredWhenNoActiveMode() {
        assertFalse(shouldRestoreFreeDriving(isNavigating = false, freeDrivingActive = false))
        assertFalse(shouldRestoreFreeDriving(isNavigating = true, freeDrivingActive = false))
    }

    // ── at most one restore per session (spec: auto/free-driving) ──

    @Test
    fun aSessionWithoutPushRestoresFreeDriving() {
        val gate = FreeDrivingRestoreGate()

        assertFalse(gate.hasRestored)
        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }

    @Test
    fun theSecondSessionPathDoesNotPushAnotherFreeDrivingView() {
        // onCreateScreen and onWarmupComplete both ask; the free-driving flag survives
        // a session destroy, so an unguarded restore pushed two views (two renderers,
        // and a ghost view under the top one).
        val gate = FreeDrivingRestoreGate()
        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
        gate.recordPush()

        assertFalse(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
        assertTrue(gate.hasRestored)
    }

    @Test
    fun aRefusedPushIsNotConsumedAndIsRetried() {
        // The session records the push only when it actually pushed: with the session
        // not started the attempt is deferred and the next started sync restores it.
        val gate = FreeDrivingRestoreGate()

        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
        assertFalse(gate.hasRestored)
        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }

    @Test
    fun startupRetryAllowsOneMoreRestore() {
        // retryStartup pops back to the root, so there is no free-driving view left
        // to restore from and a still-active mode may be pushed once more.
        val gate = FreeDrivingRestoreGate()
        gate.recordPush()
        assertFalse(gate.shouldPush(isNavigating = false, freeDrivingActive = true))

        gate.reset()

        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }

    @Test
    fun noRestoreIsConsumedWhileNavigatingOrWithoutAnActiveMode() {
        val gate = FreeDrivingRestoreGate()

        assertFalse(gate.shouldPush(isNavigating = true, freeDrivingActive = true))
        assertFalse(gate.shouldPush(isNavigating = false, freeDrivingActive = false))
        assertFalse("a refused attempt consumes nothing", gate.hasRestored)
        assertTrue(gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }

    // ── the push outcome drives the one-shot (spec: car-host-fault-isolation — Host screen-stack mutations are balanced) ──

    @Test
    fun aRejectedPushDoesNotConsumeTheOneShotRestore() {
        // The session records the push only when the host accepted it: consuming the one-shot
        // on a refused push loses the free-driving view for the rest of the session, because
        // no session path asks a second time.
        val gate = FreeDrivingRestoreGate()

        gate.recordPush(landed = false)

        assertFalse("a refused push consumes nothing", gate.hasRestored)
        assertTrue("the next started sync restores it", gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }

    @Test
    fun aLandedPushConsumesTheOneShotRestore() {
        val gate = FreeDrivingRestoreGate()

        gate.recordPush(landed = true)

        assertTrue(gate.hasRestored)
        assertFalse("one restore per session", gate.shouldPush(isNavigating = false, freeDrivingActive = true))
    }
}
