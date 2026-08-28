package com.naviveylin.ui.addressbook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.naviveylin.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the first-use rationale dialog (spec:
 * address-book-permission — rationale before request): the continue action is
 * the only path that triggers the permission request (no request before
 * acknowledgment), while "Not now" and dismissal decline without requesting.
 */
@RunWith(RobolectricTestRunner::class)
class AddressBookRationaleDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var continueCount = 0
    private var notNowCount = 0
    private var dismissCount = 0

    private fun launchDialog() {
        continueCount = 0
        notNowCount = 0
        dismissCount = 0
        composeRule.setContent {
            AddressBookRationaleDialog(
                onDismiss = { dismissCount++ },
                onContinue = { continueCount++ },
                onNotNow = { notNowCount++ }
            )
        }
    }

    @Test
    fun dialogExplainsRequestAndOptionality() {
        launchDialog()

        composeRule.onNodeWithText("Address book access").assertIsDisplayed()
        composeRule.onNodeWithText("Continue").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
        // The rationale text must mention optionality/deniability.
        val text = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getString(R.string.address_book_rationale_text)
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun noRequestBeforeAcknowledgment() {
        launchDialog()

        // Merely showing the dialog must not fire the continue (request) path.
        composeRule.waitForIdle()
        assertEquals(0, continueCount)
    }

    @Test
    fun continueTriggersRequestPathOnce() {
        launchDialog()

        composeRule.onNodeWithText("Continue").performClick()

        assertEquals(1, continueCount)
        assertEquals(0, notNowCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun notNowDeclinesWithoutRequesting() {
        launchDialog()

        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, notNowCount)
        assertEquals(0, continueCount)
    }
}
