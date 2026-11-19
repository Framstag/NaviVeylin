package com.naviveylin.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import com.naviveylin.data.SettingsStorage
import com.naviveylin.test.ComposeSandboxRule
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The announcement switches on the phone's settings surface (spec: `voice-guidance` — "The phone
 * offers both switches in its settings sheet"; tasks.md 5.1).
 *
 * The rows are the registry's, not a fixture: the point of these cases is that the phone's voice
 * switch and the shared GPS-announcement switch are reachable and persisted from the surface, and a
 * fixture table would assert the renderer against rows the app never shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsSurfaceVoiceSwitchesTest {

    @get:Rule(order = 0)
    val composeSandbox = ComposeSandboxRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var controller: SettingsController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        controller = SettingsController(SettingsStorage(context))
    }

    /**
     * Both switches are on the topic page with their stored values, and toggling either persists it
     * (spec: `voice-guidance` — "The phone offers both switches in its settings sheet"; tasks.md 5.1
     * `bothSwitchesAreShownAndPersisted`).
     */
    @Test
    fun bothSwitchesAreShownAndPersisted() {
        launchHintsTopic()

        composeRule.onNodeWithText(context.getString(R.string.voice_guidance_switch))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.gps_announcements_switch))
            .performScrollTo()
            .assertIsDisplayed()

        // The stored defaults: the phone speaks, the GPS announcements are off.
        switchOf(VOICE_ROW).assertIsOn()
        switchOf(GPS_ROW).assertIsOff()

        switchOf(VOICE_ROW).performClick()
        switchOf(GPS_ROW).performClick()
        awaitStored { settings -> !settings.voiceGuidanceEnabled && settings.announceGpsState }

        val stored = runBlocking { SettingsStorage(context).load() }
        assertFalse("the phone's voice switch is persisted off", stored.voiceGuidanceEnabled)
        assertTrue("the GPS-announcement switch is persisted on", stored.announceGpsState)
        assertTrue("the car's own voice switch is untouched", stored.autoVoiceGuidanceEnabled)
    }

    // ---- fixtures ----

    /**
     * Wait until the settings file holds a document [holds] accepts: the write is a coroutine on the
     * rule's scheduler and lands in the file on the storage's own dispatcher, so the click alone is
     * not the persisted value.
     */
    private fun awaitStored(holds: (com.naviveylin.data.AppSettings) -> Boolean) {
        composeRule.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { SettingsStorage(context).load() }.let(holds)
        }
    }

    private fun launchHintsTopic() {
        // The surface reads the document the host loaded on its start path (design D2), so the fixture
        // loads it before the page is opened rather than relying on the defaults the controller holds.
        runBlocking { controller.load() }
        composeRule.setContent {
            SettingsSurfaceHost(
                controller = controller,
                onOpenMapManager = {},
                onOpenAbout = {}
            )
        }
        composeRule.runOnIdle { controller.openTopic(SettingsTopic.HINTS_AND_LANGUAGE) }
        composeRule.waitForIdle()
    }

    /**
     * The one switch a row carries: the row's tag is on the label column, so the control is the
     * toggleable node below it rather than a tag the renderer would carry for the test's sake.
     */
    private fun switchOf(key: String): SemanticsNodeInteraction = composeRule.onNode(
        isToggleable() and hasAnyAncestor(hasTestTag(SETTINGS_ROW_TAG_PREFIX + key)),
        useUnmergedTree = true
    )

    private companion object {
        const val VOICE_ROW = "voice-guidance-phone"
        const val GPS_ROW = "gps-announcements"
    }
}
