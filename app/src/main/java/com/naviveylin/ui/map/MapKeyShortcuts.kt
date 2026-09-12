package com.naviveylin.ui.map

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Map-canvas keyboard shortcuts (spec: keyboard-shortcuts): `/` opens the
 * search dialog, `+`/`=` zoom in, `-` zooms out. Pure mapping from a key
 * event to a callback so it is unit-testable without a composed screen.
 * Returns true when the event was consumed.
 */
internal fun dispatchMapCanvasKey(
    event: KeyEvent,
    onOpenSearch: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyUp) return false
    return when (event.key) {
        Key.Slash -> {
            onOpenSearch()
            true
        }
        Key.Plus, Key.Equals -> {
            onZoomIn()
            true
        }
        Key.Minus -> {
            onZoomOut()
            true
        }
        else -> false
    }
}
