package com.naviveylin.ui.map

import androidx.compose.ui.input.key.KeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for the map-canvas keyboard shortcuts (spec: keyboard-shortcuts):
 * `/` opens the search dialog, `+` / `=` zoom in, `-` zooms out; other keys
 * and non-key-up events are not consumed.
 */
@RunWith(RobolectricTestRunner::class)
class MapKeyShortcutsTest {

    private fun keyEvent(action: Int, keyCode: Int): KeyEvent =
        KeyEvent(AndroidKeyEvent(action, keyCode))

    private fun keyUpAndroid(keyCode: Int): KeyEvent =
        keyEvent(AndroidKeyEvent.ACTION_UP, keyCode)

    @Test
    fun slashOpensSearch() {
        var searchOpened = false
        val consumed = dispatchMapCanvasKey(
            keyUpAndroid(AndroidKeyEvent.KEYCODE_SLASH),
            onOpenSearch = { searchOpened = true },
            onZoomIn = {},
            onZoomOut = {}
        )

        assertTrue("slash must be consumed", consumed)
        assertTrue("slash must open search", searchOpened)
    }

    @Test
    fun plusAndEqualsZoomIn() {
        var zoomInCount = 0
        for (keyCode in intArrayOf(AndroidKeyEvent.KEYCODE_PLUS, AndroidKeyEvent.KEYCODE_EQUALS)) {
            val consumed = dispatchMapCanvasKey(
                keyUpAndroid(keyCode),
                onOpenSearch = {},
                onZoomIn = { zoomInCount++ },
                onZoomOut = {}
            )
            assertTrue("$keyCode must be consumed", consumed)
        }
        assertEquals(2, zoomInCount)
    }

    @Test
    fun minusZoomsOut() {
        var zoomOutCount = 0
        val consumed = dispatchMapCanvasKey(
            keyUpAndroid(AndroidKeyEvent.KEYCODE_MINUS),
            onOpenSearch = {},
            onZoomIn = {},
            onZoomOut = { zoomOutCount++ }
        )

        assertTrue("minus must be consumed", consumed)
        assertEquals(1, zoomOutCount)
    }

    @Test
    fun otherKeysNotConsumed() {
        val consumed = dispatchMapCanvasKey(
            keyUpAndroid(AndroidKeyEvent.KEYCODE_A),
            onOpenSearch = {},
            onZoomIn = {},
            onZoomOut = {}
        )

        assertFalse("unmapped key must not be consumed", consumed)
    }

    @Test
    fun keyDownNotConsumed() {
        val consumed = dispatchMapCanvasKey(
            keyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_SLASH),
            onOpenSearch = {},
            onZoomIn = {},
            onZoomOut = {}
        )

        assertFalse("key-down must not trigger the action", consumed)
    }
}
