package com.naviveylin.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider

/**
 * Compose tests for the navigation gate's refusal dialog (spec:
 * `location-permissions` — Starting navigation requires precise location / Phone
 * offers the upgrade action). Default Robolectric sandbox, no `@Config` — see the
 * AGENTS.md classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class PreciseLocationRequiredDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun message(): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.naviveylin.core.R.string.location_precise_required_navigation)

    private fun actionLabel(): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.naviveylin.core.R.string.location_grant_precise_action)

    @Test
    fun dialogShowsTheSharedWordingAndTheUpgradeAction() {
        composeRule.setContent {
            PreciseLocationRequiredDialog(onDismiss = {}, onUpgrade = {})
        }

        composeRule.onNodeWithText(message()).assertIsDisplayed()
        composeRule.onNodeWithText(actionLabel()).assertIsDisplayed()
    }

    @Test
    fun confirmInvokesTheUpgrade() {
        var upgraded = false
        composeRule.setContent {
            PreciseLocationRequiredDialog(onDismiss = {}, onUpgrade = { upgraded = true })
        }

        composeRule.onNodeWithText(actionLabel()).performClick()

        assertTrue("the action must offer the upgrade", upgraded)
    }

    @Test
    fun dismissInvokesTheDismissal() {
        var dismissed = false
        composeRule.setContent {
            PreciseLocationRequiredDialog(onDismiss = { dismissed = true }, onUpgrade = {})
        }

        composeRule.onNodeWithText(
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(com.naviveylin.R.string.cancel)
        ).performClick()

        assertTrue("dismissing must not start anything", dismissed)
    }

    @Test
    fun upgradeActionPrefersTheRequestWhileTheSystemCanStillAsk() {
        assertEquals(
            PreciseLocationAction.REQUEST_PERMISSION,
            preciseLocationAction(permanentlyDenied = false)
        )
    }

    @Test
    fun upgradeActionFallsBackToSettingsWhenTheSystemWillNotAskAgain() {
        assertEquals(
            PreciseLocationAction.OPEN_SETTINGS,
            preciseLocationAction(permanentlyDenied = true)
        )
    }
}
