package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the navigation screen strip actions (spec: auto-map-layout —
 * actions on the left edge, visualisations on the right edge) stay usable
 * while driving.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationScreenActionsTest {

    @Test
    fun zoomInIsNotParkedOnly() {
        assertFalse(NavigationScreenActions.zoomInAction {}.onClickDelegate?.isParkedOnly == true)
    }

    @Test
    fun zoomOutIsNotParkedOnly() {
        assertFalse(NavigationScreenActions.zoomOutAction {}.onClickDelegate?.isParkedOnly == true)
    }

    @Test
    fun stopIsNotParkedOnly() {
        // Stopping navigation / exiting free driving mid-drive must work
        // (spec: auto/free-driving — "Exit free driving", auto/navigation-view
        // — "Leave navigation at any time").
        assertFalse(NavigationScreenActions.stopAction {}.onClickDelegate?.isParkedOnly == true)
    }

    @Test
    fun stopCarriesIconAndNoTitle() {
        // Map action strip constraint: icon-only, no custom title.
        val action = NavigationScreenActions.stopAction {}
        assertEquals(null, action.title)
        assertTrue(action.icon != null)
    }

    @Test
    fun stopInvokesCallback() {
        var clicked = false
        val action = NavigationScreenActions.stopAction { clicked = true }
        action.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(clicked)
    }

    @Test
    fun routeListIsNotParkedOnly() {
        // Opening the route description mid-drive must work (spec:
        // auto/navigation-view — "Route description screen").
        assertFalse(NavigationScreenActions.routeListAction {}.onClickDelegate?.isParkedOnly == true)
    }

    @Test
    fun routeListCarriesIconAndNoTitle() {
        // Map action strip constraint: icon-only, no custom title.
        val action = NavigationScreenActions.routeListAction {}
        assertEquals(null, action.title)
        assertTrue(action.icon != null)
    }

    @Test
    fun routeListInvokesCallback() {
        var clicked = false
        val action = NavigationScreenActions.routeListAction { clicked = true }
        action.onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(clicked)
    }
}
