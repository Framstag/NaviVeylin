package com.naviveylin.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the map screen host-strip actions (spec: auto-map-layout — left
 * map action strip icon-only with menu/search parked-only and settings
 * driving-safe; right strip compass driving-safe and zoom parked-only).
 */
@RunWith(RobolectricTestRunner::class)
class MapStripActionsTest {

    @Test
    fun searchIsParkedOnlyAndIconOnly() {
        val action = MapStripActions.searchAction {}
        assertTrue(action.onClickDelegate?.isParkedOnly == true)
        assertNull(action.title)
        assertTrue(action.icon != null)
    }

    @Test
    fun settingsIsDrivingSafeAndIconOnly() {
        val action = MapStripActions.settingsAction {}
        assertFalse(action.onClickDelegate?.isParkedOnly == true)
        assertNull(action.title)
        assertTrue(action.icon != null)
    }

    @Test
    fun zoomIsParkedOnlyAndTitled() {
        assertTrue(MapStripActions.zoomInAction {}.onClickDelegate?.isParkedOnly == true)
        assertTrue(MapStripActions.zoomOutAction {}.onClickDelegate?.isParkedOnly == true)
        assertEquals("+", MapStripActions.zoomInAction {}.title.toString())
        assertEquals("-", MapStripActions.zoomOutAction {}.title.toString())
    }

    @Test
    fun infoIsDrivingSafeAndIconOnly() {
        val action = MapStripActions.infoAction {}
        assertFalse(action.onClickDelegate?.isParkedOnly == true)
        assertNull(action.title)
        assertTrue(action.icon != null)
    }
}
