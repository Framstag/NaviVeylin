package com.naviveylin.auto

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
}
