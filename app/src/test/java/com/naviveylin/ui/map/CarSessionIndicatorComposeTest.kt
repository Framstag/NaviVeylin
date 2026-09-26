package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose tests for the car-session advisory [CarSessionIndicator] (spec:
 * `car-session-presence` — The phone is informed, not disabled).
 *
 * The indicator is a label, not a control: it renders the translatable string, is
 * displayed while shown, and carries no click action that could gate a map or
 * navigation action. The screen-level wiring (the flag decides whether it is
 * composed at all) lives in `MapCanvasScreen`; the state transitions of the signal
 * itself are covered by `CarSessionPresenceImplTest`.
 */
@RunWith(RobolectricTestRunner::class)
class CarSessionIndicatorComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun label(): String =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.naviveylin.R.string.car_session_active_indicator)

    @Test
    fun theIndicatorShowsTheTranslatedLabel() {
        composeRule.setContent {
            Box(Modifier.size(240.dp)) { CarSessionIndicator() }
        }

        composeRule.onNodeWithText(label()).assertIsDisplayed()
    }

    @Test
    fun theIndicatorIsNotAControl() {
        // Spec: "The phone is informed, not disabled" — the indication gates nothing,
        // so it must carry no click action that could swallow a map gesture.
        composeRule.setContent {
            Box(Modifier.size(240.dp)) { CarSessionIndicator() }
        }

        composeRule.onNodeWithText(label()).assertHasNoClickAction()
    }
}
