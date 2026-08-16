package com.naviveylin.ui.map

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric Compose tests for [KeepScreenOnEffect] (the keep-screen-on behavior
 * used by [MapCanvasScreen]): flag applied while enabled, cleared when disabled,
 * cleared on pause and re-applied on resume, cleared when leaving composition.
 *
 * JNI-free by design: no FakeOSMScoutClient / OSMScoutClient touch, no @Config.
 */
@RunWith(RobolectricTestRunner::class)
class KeepScreenOnEffectTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun hasKeepScreenOnFlag(): Boolean {
        val flags = composeRule.activity.window.attributes.flags
        return flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
    }

    @Test
    fun flagSetWhenEnabled() {
        composeRule.setContent { KeepScreenOnEffect(keepScreenOn = true) }
        composeRule.waitForIdle()
        assertTrue(hasKeepScreenOnFlag())
    }

    @Test
    fun flagClearedWhenDisabled() {
        val keepOn = mutableStateOf(true)
        composeRule.setContent { KeepScreenOnEffect(keepScreenOn = keepOn.value) }
        composeRule.waitForIdle()
        assertTrue(hasKeepScreenOnFlag())

        composeRule.runOnUiThread { keepOn.value = false }
        composeRule.waitForIdle()
        assertFalse(hasKeepScreenOnFlag())
    }

    @Test
    fun flagClearedOnPauseAndReappliedOnResume() {
        composeRule.setContent { KeepScreenOnEffect(keepScreenOn = true) }
        composeRule.waitForIdle()
        assertTrue(hasKeepScreenOnFlag())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        assertFalse(hasKeepScreenOnFlag())

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertTrue(hasKeepScreenOnFlag())
    }

    @Test
    fun flagClearedWhenLeavingComposition() {
        val show = mutableStateOf(true)
        composeRule.setContent {
            if (show.value) KeepScreenOnEffect(keepScreenOn = true)
        }
        composeRule.waitForIdle()
        assertTrue(hasKeepScreenOnFlag())

        composeRule.runOnUiThread { show.value = false }
        composeRule.waitForIdle()
        assertFalse(hasKeepScreenOnFlag())
    }
}
