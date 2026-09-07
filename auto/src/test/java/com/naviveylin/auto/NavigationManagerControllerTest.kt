package com.naviveylin.auto

import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [NavigationManagerController] (spec: auto/navigation-view — "Leave
 * navigation at any time"): the host ETA card stop button is delivered as
 * [NavigationManagerCallback.onStopNavigation], which the host only sends when
 * the app registered a callback and called [NavigationManager.navigationStarted].
 * The session itself needs a host-provided CarContext and cannot be
 * constructed in Robolectric, so the controller is the testable seam (same
 * pattern as [NavigationSessionDarkModeTest]).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationManagerControllerTest {

    @Test
    fun navigationStartRegistersCallbackAndStarts() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs

        NavigationManagerController(nm) {}.onNavigationStarted()

        verify { nm.setNavigationManagerCallback(any()) }
        verify { nm.navigationStarted() }
    }

    @Test
    fun navigationEndEndsThenClearsCallback() {
        val nm = mockk<NavigationManager>()
        every { nm.navigationEnded() } just Runs
        every { nm.clearNavigationManagerCallback() } just Runs

        NavigationManagerController(nm) {}.onNavigationEnded()

        // Order matters: clearNavigationManagerCallback() throws while
        // navigating, so navigationEnded() must run first.
        verify { nm.navigationEnded() }
        verify { nm.clearNavigationManagerCallback() }
    }

    @Test
    fun hostStopInvokesStopHandler() {
        val nm = mockk<NavigationManager>()
        every { nm.setNavigationManagerCallback(any()) } just Runs
        every { nm.navigationStarted() } just Runs
        var stopped = false
        val controller = NavigationManagerController(nm) { stopped = true }

        controller.onNavigationStarted()
        val callback = slot<NavigationManagerCallback>()
        verify { nm.setNavigationManagerCallback(capture(callback)) }

        callback.captured.onStopNavigation()

        assertTrue("host stop must stop navigation", stopped)
    }

    @Test
    fun destroyWhileNavigatingDoesNotThrow() {
        val nm = mockk<NavigationManager>()
        every { nm.navigationEnded() } just Runs
        every { nm.clearNavigationManagerCallback() } just Runs

        // Must not throw even though the controller may be mid-navigation.
        NavigationManagerController(nm) {}.onDestroy()

        verify { nm.navigationEnded() }
        verify { nm.clearNavigationManagerCallback() }
    }
}
