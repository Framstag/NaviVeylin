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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import com.naviveylin.data.AppSettings
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.toAppSettings
import com.naviveylin.data.toAutoSettings
import com.naviveylin.test.ComposeSandboxRule
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car's own voice switch as the phone offers it (spec: `voice-guidance` — "The phone offers the
 * car's own voice switch"; tasks.md 5.2).
 *
 * The row is a car row: it is marked for Android Auto, and its write reaches the car's stored value
 * and nothing else — the phone's own switch is a different field and stays as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsSurfaceCarVoiceRowTest {

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
     * The car's row carries the Android Auto mark and writes only the car's value (spec:
     * `voice-guidance` — "The car's switch is edited on the phone"; tasks.md 5.2
     * `theCarRowWritesOnlyTheCarValue`).
     */
    @Test
    fun theCarRowWritesOnlyTheCarValue() {
        launchHintsTopic()

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + CAR_ROW)
            .performScrollTo()
            .assertIsDisplayed()
        // The mark names the surface the row applies to.
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.settings_scope_car)
        ).assertIsDisplayed()
        switchOf(CAR_ROW).assertIsOn()

        switchOf(CAR_ROW).performClick()
        awaitStored { settings -> !settings.autoVoiceGuidanceEnabled }

        val stored = runBlocking { SettingsStorage(context).load() }
        assertFalse("the car's stored voice switch is off", stored.autoVoiceGuidanceEnabled)
        assertTrue("the phone's own voice switch is unchanged", stored.voiceGuidanceEnabled)
        // The row shows the value it wrote, not the phone's.
        switchOf(CAR_ROW).assertIsOff()
        switchOf(PHONE_ROW).assertIsOn()
    }

    /**
     * A value the car itself wrote is what the phone's row shows (spec: `voice-guidance` — "A car
     * change is visible on the phone"; tasks.md 5.2 `theCarRowShowsTheCarValue`).
     */
    @Test
    fun theCarRowShowsTheCarValue() {
        // The car writes its own value through the AutoSettings mapping, exactly as the car's
        // preferences surface does.
        runBlocking {
            val storage = SettingsStorage(context)
            val carSettings = storage.load().toAutoSettings().copy(voiceGuidanceEnabled = false)
            storage.update { current -> carSettings.toAppSettings(current) }
        }

        launchHintsTopic()

        switchOf(CAR_ROW).assertIsOff()
        switchOf(PHONE_ROW).assertIsOn()
    }

    // ---- fixtures ----

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

    private fun switchOf(key: String): SemanticsNodeInteraction = composeRule.onNode(
        isToggleable() and hasAnyAncestor(hasTestTag(SETTINGS_ROW_TAG_PREFIX + key)),
        useUnmergedTree = true
    )

    /**
     * Wait until the settings file holds a document [holds] accepts: the row's write is a coroutine on
     * the rule's scheduler and lands in the file on the storage's own dispatcher.
     */
    private fun awaitStored(holds: (AppSettings) -> Boolean) {
        composeRule.waitForIdle()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { SettingsStorage(context).load() }.let(holds)
        }
    }

    private companion object {
        const val CAR_ROW = "voice-guidance-car"
        const val PHONE_ROW = "voice-guidance-phone"
    }
}
