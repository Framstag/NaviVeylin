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
}
