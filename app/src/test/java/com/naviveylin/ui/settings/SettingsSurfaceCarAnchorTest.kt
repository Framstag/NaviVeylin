package com.naviveylin.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import com.naviveylin.core.VehicleAnchorPosition
import com.naviveylin.data.AppSettings
import com.naviveylin.data.SettingsStorage
import com.naviveylin.data.toAutoSettings
import com.naviveylin.test.ComposeSandboxRule
import com.naviveylin.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The car's own anchor rows as the phone offers them (spec: `settings-surface` — "The phone edits the
 * settings Android Auto owns"; tasks.md 4.4, 4.5).
 *
 * A car anchor is a tri-state value: the car has no anchor of its own and inherits the phone's, it
 * holds a preset of its own, or the user returns it to the inheriting state. The stored field is the
 * state: null means "inherit", and nothing else writes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsSurfaceCarAnchorTest {

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
     * A car anchor that was never set shows the inheriting state, and the picker marks that state as
     * the current one (spec: `settings-surface` — "An inheriting anchor is shown as inheriting";
     * tasks.md 4.4 `anInheritingAnchorIsShownAsInheriting`).
     */
    @Test
    fun anInheritingAnchorIsShownAsInheriting() {
        launchNavigationTopic()

        // Both car rows state the inheriting value: the state is a value of the row, not a blank line.
        composeRule.onAllNodesWithText(label(R.string.settings_anchor_inherit))
            .assertCountEquals(2)

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + CAR_ROUTING_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SELECT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + INHERIT)
            .assertIsSelected()
    }

    /**
     * Selecting the inheriting option clears the car's own value, so the car follows the phone from
     * then on (spec: `settings-surface` — "Returning to the phone value"; tasks.md 4.4
     * `returningToThePhoneValueClearsTheCarValue`).
     */
    @Test
    fun returningToThePhoneValueClearsTheCarValue() {
        // The car holds an anchor of its own, written the way the car's own picker writes it.
        runBlocking {
            SettingsStorage(context).update {
                it.copy(autoRoutingAnchorId = VehicleAnchorPosition.TOP_LEFT.id)
            }
        }

        launchNavigationTopic()

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + CAR_ROUTING_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTINGS_SELECT_OPTION_TAG_PREFIX + INHERIT).performClick()
        awaitStored { settings -> settings.autoRoutingAnchorId == null }

        val stored = runBlocking { SettingsStorage(context).load() }
        assertNull("the car keeps no anchor of its own", stored.autoRoutingAnchorId)
        // And it follows the phone's anchor, which the shared mapping is the one place to read.
        assertEquals(
            "the car reads the phone's anchor again",
            stored.routingAnchorId,
            stored.toAutoSettings().routingAnchorId
        )
    }

    /**
     * While the car inherits, a phone anchor change is what the car reads on its next session (spec:
     * `auto-map-layout` — "Car falls back to the phone value"; tasks.md 4.4
     * `changingThePhoneAnchorChangesAnInheritingCarAnchor`).
     */
    @Test
    fun changingThePhoneAnchorChangesAnInheritingCarAnchor() {
        launchNavigationTopic()

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + PHONE_ROUTING_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(
            SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.BOTTOM_RIGHT.id
        ).performClick()
        awaitStored { settings -> settings.routingAnchorId == VehicleAnchorPosition.BOTTOM_RIGHT.id }

        val stored = runBlocking { SettingsStorage(context).load() }
        assertNull("the car still owns no anchor", stored.autoRoutingAnchorId)
        assertEquals(
            "the inheriting car reads the phone's new anchor",
            VehicleAnchorPosition.BOTTOM_RIGHT.id,
            stored.toAutoSettings().routingAnchorId
        )
    }

    /**
     * A car anchor that was set does not follow the phone: the phone's change reaches the phone's own
     * field only (spec: `auto-map-layout` — "Car falls back to the phone value" applies until the car
     * has a value; tasks.md 4.5 `aSetCarValueSurvivesAPhoneAnchorChange`).
     */
    @Test
    fun aSetCarValueSurvivesAPhoneAnchorChange() {
        launchNavigationTopic()

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + CAR_ROUTING_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.TOP_LEFT.id
        ).performClick()
        awaitStored { settings -> settings.autoRoutingAnchorId == VehicleAnchorPosition.TOP_LEFT.id }

        composeRule.onNodeWithTag(SETTINGS_ROW_TAG_PREFIX + PHONE_ROUTING_ROW)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(
            SETTINGS_SELECT_OPTION_TAG_PREFIX + VehicleAnchorPosition.BOTTOM_RIGHT.id
        ).performClick()
        awaitStored { settings -> settings.routingAnchorId == VehicleAnchorPosition.BOTTOM_RIGHT.id }

        val stored = runBlocking { SettingsStorage(context).load() }
        assertEquals(
            "the car's own anchor is untouched by the phone's change",
            VehicleAnchorPosition.TOP_LEFT.id,
            stored.autoRoutingAnchorId
        )
        assertEquals(
            "the car reads its own anchor",
            VehicleAnchorPosition.TOP_LEFT.id,
            stored.toAutoSettings().routingAnchorId
        )
    }

    // ---- fixtures ----

    private fun launchNavigationTopic() {
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
        composeRule.runOnIdle { controller.openTopic(SettingsTopic.NAVIGATION_AND_ROUTE) }
        composeRule.waitForIdle()
    }

    /** A resource-backed label, resolved the way the surface resolves it. */
    private fun label(res: Int): String = context.getString(res)

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
        const val CAR_ROUTING_ROW = "car-routing-anchor"
        const val PHONE_ROUTING_ROW = "routing-anchor"

        /** The picker option that stands for "inherits the phone's value": the empty stored id. */
        const val INHERIT = ""
    }
}
