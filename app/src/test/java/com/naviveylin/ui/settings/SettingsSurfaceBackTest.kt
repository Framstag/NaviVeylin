package com.naviveylin.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.ComposeSandboxRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * System back inside the settings surface (spec: `settings-surface` — "The surface opens at the root
 * and dismisses to the map"; spec: `back-gesture` — "Back closes the settings surface one level at a
 * time"; tasks.md 2.4 `backLeavesTheTopicThenTheSurface`).
 *
 * Back is the surface's own level-by-level dismissal, not a host's: a topic returns to the root, the
 * root closes the surface, and the map screen underneath keeps its own back behaviour (asserted here
 * as "the surface is gone and the band below it is still composed").
 */
@RunWith(RobolectricTestRunner::class)
class SettingsSurfaceBackTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    @Test
    fun backLeavesTheTopicThenTheSurface() {
        val controller = SettingsController(
            SettingsStorage(ApplicationProvider.getApplicationContext())
        )
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                // The map screen underneath: it must still be composed when the surface closes.
                Box(Modifier.fillMaxSize().testTag("map-frame"))
                SettingsSurfaceHost(
                    controller = controller,
                    onOpenMapManager = {},
                    onOpenAbout = {}
                )
            }
        }
        composeRule.runOnIdle { controller.openTopic(SettingsTopic.MAP) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SETTINGS_TOPIC_TAG).assertIsDisplayed()

        pressBack()

        assertEquals(
            "back on a topic page shows the root",
            SettingsSurfaceState.Root,
            controller.surfaceState.value
        )
        composeRule.onNodeWithTag(SETTINGS_ROOT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(SettingsTopic.MAP.titleRes)
        ).assertIsDisplayed()

        pressBack()

        assertEquals(
            "back on the root closes the surface",
            SettingsSurfaceState.Closed,
            controller.surfaceState.value
        )
        composeRule.onNodeWithTag(SETTINGS_SURFACE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("map-frame").assertIsDisplayed()
    }
}
