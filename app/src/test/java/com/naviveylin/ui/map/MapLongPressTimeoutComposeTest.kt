package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The map canvas's long press follows the configured timeout instead of a literal
 * (spec: `map-long-press-timeout` — The long-press timeout is a configuration).
 *
 * Time is the test clock's, not the wall clock's: `mainClock.advanceTimeBy` drives the
 * handler's own timeout, so no test here waits (guidelines/Build.md §6e).
 */
@RunWith(RobolectricTestRunner::class)
class MapLongPressTimeoutComposeTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createComposeRule()

    private var longPresses = 0

    private val callbacks = object : MapGestureCallbacks {
        override fun onPan(dx: Float, dy: Float) {}
        override fun onCentroidPan(dx: Float, dy: Float) {}
        override fun onRotate(angleDeltaRadians: Double) {}
        override fun onZoom(centroid: Offset, zoomFactor: Float) {}
        override fun onLongPress(position: Offset) { longPresses++ }
        override fun onRenderRequested() {}
    }

    private fun launchMap(
        platformLongPressTimeoutMs: Long? = null,
        configuredLongPressTimeoutMs: Long? = null,
    ) {
        composeRule.setContent {
            val platform = LocalViewConfiguration.current
            val configured = object : ViewConfiguration by platform {
                override val longPressTimeoutMillis: Long =
                    platformLongPressTimeoutMs ?: platform.longPressTimeoutMillis
            }
            CompositionLocalProvider(LocalViewConfiguration provides configured) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .mapGestureHandler(callbacks, configuredLongPressTimeoutMs)
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun longPressHonoursTheConfiguredPlatformTimeout() {
        launchMap(platformLongPressTimeoutMs = 900L)

        composeRule.onRoot().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        assertEquals(
            "a press held 600 ms is not a long press when the configured timeout is 900 ms",
            0,
            longPresses
        )

        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        assertEquals(
            "a press held past the configured 900 ms is a long press",
            1,
            longPresses
        )
    }

    @Test
    fun longPressUsesTheConfiguredAppTimeout() {
        // The platform timeout is pinned at 500 ms here rather than taken from the environment,
        // so the discrimination is this case's own: a press that survives 600 ms proves the app's
        // configured 900 ms decided it, not the platform's 500 ms.
        launchMap(platformLongPressTimeoutMs = 500L, configuredLongPressTimeoutMs = 900L)

        composeRule.onRoot().performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()
        assertEquals(
            "a press held 600 ms is not a long press when the app configures 900 ms",
            0,
            longPresses
        )

        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        assertEquals(
            "a press held past the app's configured 900 ms is a long press",
            1,
            longPresses
        )
    }
}
