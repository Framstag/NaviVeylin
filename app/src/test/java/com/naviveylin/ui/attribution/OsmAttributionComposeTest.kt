package com.naviveylin.ui.attribution

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Compose UI tests for the OSM attribution overlay (spec: osm-attribution).
 * NOTE: must run under the DEFAULT Robolectric sandbox (no @Config(sdk=...)
 * or @GraphicsMode) so the JNI stub .so loads in the shared classloader —
 * see AGENTS.md classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class OsmAttributionComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val attributionText = "© OpenStreetMap contributors"
    private val infoButtonDesc = "Map data licence information"
    private val copyrightUrl = "https://www.openstreetmap.org/copyright"

    @Test
    fun attributionShownOnMapLoad() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.onNodeWithText(attributionText).assertIsDisplayed()
    }

    @Test
    fun attributionAutoHidesAfterFiveSeconds() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.onNodeWithText(attributionText).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(attributionText).assertDoesNotExist()
    }

    @Test
    fun attributionReappearsOnInteraction() {
        var tick by mutableStateOf(0)
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = tick)
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(attributionText).assertDoesNotExist()

        // Simulate a map interaction: the notice re-shows and the timer restarts.
        tick = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText(attributionText).assertIsDisplayed()
    }

    @Test
    fun attributionTapOpensCopyrightPage() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.onNodeWithText(attributionText).performClick()

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(copyrightUrl, started.dataString)
    }

    @Test
    fun infoButtonOpensLicenceDialog() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.onNodeWithContentDescription(infoButtonDesc).performClick()

        composeRule.onNodeWithText("Map data licence").assertIsDisplayed()
        composeRule.onNodeWithText("Open Database License", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("openstreetmap.org/copyright").assertIsDisplayed()
    }

    @Test
    fun infoButtonRemainsWhenAttributionHidden() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(attributionText).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(infoButtonDesc).assertIsDisplayed()
    }

    @Test
    fun licenceDialogLinkOpensCopyrightPage() {
        composeRule.setContent {
            OsmAttributionOverlay(interactionTick = 0)
        }
        composeRule.onNodeWithContentDescription(infoButtonDesc).performClick()
        composeRule.onNodeWithText("openstreetmap.org/copyright").performClick()

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(copyrightUrl, started.dataString)
    }

    @Test
    fun openUrlLaunchesViewIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        openUrl(context, copyrightUrl)

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(copyrightUrl, started.dataString)
    }
}
