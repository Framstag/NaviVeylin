package com.naviveylin.auto

import androidx.car.app.model.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun navigationMapActionStripHasPanAndRouteList() {
        // The navigation map action strip carries the pan action (spec:
        // auto/map-pan) and the route-description action — no stop (the host
        // ETA card stop button is the single stop affordance, spec:
        // auto/navigation-view "Leave navigation at any time") and no back
        // button.
        val strip = NavigationScreenActions.navigationMapActionStrip {}
        assertEquals(2, strip.actions.size)
        // PAN is a standard host action (no title, no click delegate).
        assertEquals(Action.TYPE_PAN, strip.actions[0].type)
        assertTrue(strip.actions[0].isStandard)
        // Route-list is the app action: icon-only (map strip constraint).
        assertNull(strip.actions[1].title)
        assertTrue(strip.actions[1].icon != null)
    }

    @Test
    fun navigationMapActionStripRouteListInvokesCallback() {
        var clicked = false
        val strip = NavigationScreenActions.navigationMapActionStrip { clicked = true }
        strip.actions[1].onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(clicked)
    }

    @Test
    fun freeDrivingMapActionStripHasPanAndExit() {
        // The free-driving map action strip carries the pan action (spec:
        // auto/map-pan) and the exit "x" button (spec: auto/free-driving —
        // "Exit free driving") — no back/menu affordance.
        val strip = NavigationScreenActions.freeDrivingMapActionStrip {}
        assertEquals(2, strip.actions.size)
        assertEquals(Action.TYPE_PAN, strip.actions[0].type)
        assertTrue(strip.actions[0].isStandard)
        assertNull(strip.actions[1].title)
        assertTrue(strip.actions[1].icon != null)
    }

    @Test
    fun freeDrivingMapActionStripExitInvokesCallback() {
        var clicked = false
        val strip = NavigationScreenActions.freeDrivingMapActionStrip { clicked = true }
        strip.actions[1].onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        assertTrue(clicked)
    }
}
