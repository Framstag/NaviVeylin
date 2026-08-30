package com.naviveylin.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests for the on-map speed widget (spec: map-speed-widget):
 * badge + round max-speed sign rendering, overspeed color rule, and the
 * hidden state when no speed data is available.
 */
@RunWith(RobolectricTestRunner::class)
class SpeedWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- Overspeed color rule (pure function) ---

    @Test
    fun overLimitWhenExceedingMaxBy5Plus() {
        assertTrue(isSpeedOverLimit(56.0, 50.0))
    }

    @Test
    fun notOverLimitAtExactly5Over() {
        assertFalse(isSpeedOverLimit(55.0, 50.0))
    }

    @Test
    fun notOverLimitAtOrBelowMax() {
        assertFalse(isSpeedOverLimit(50.0, 50.0))
        assertFalse(isSpeedOverLimit(40.0, 50.0))
    }

    @Test
    fun notOverLimitWhenMaxUnknown() {
        assertFalse(isSpeedOverLimit(80.0, Double.NaN))
        assertFalse(isSpeedOverLimit(80.0, 0.0))
        assertFalse(isSpeedOverLimit(80.0, -1.0))
    }

    // --- Rendering ---

    @Test
    fun badgeShowsCurrentSpeed() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = 48.0, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedWidget").assertIsDisplayed()
        composeRule.onNodeWithTag("speedBadge").assertIsDisplayed()
        composeRule.onNodeWithText("48 km/h").assertIsDisplayed()
    }

    @Test
    fun badgeWidthStableAcrossSpeedValues() {
        // The badge reserves the widest value ("999 km/h") so it does not
        // resize when the speed changes (e.g. 48 → 120 km/h).
        var speed by mutableStateOf(48.0)
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = speed, maxSpeedKmH = 50.0)
        }
        val width48 = composeRule.onNodeWithTag("speedBadge").fetchSemanticsNode().size.width
        composeRule.runOnIdle { speed = 120.0 }
        val width120 = composeRule.onNodeWithTag("speedBadge").fetchSemanticsNode().size.width
        assertEquals(width48, width120)
    }

    @Test
    fun badgeReservesMaxWidthText() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = 48.0, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedBadgeMaxText").assertIsDisplayed()
    }

    @Test
    fun limitSignShownWhenMaxKnown() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = 48.0, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedLimitSign").assertIsDisplayed()
        composeRule.onNodeWithText("50").assertIsDisplayed()
    }

    @Test
    fun limitSignHiddenWhenMaxUnknown() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = 48.0, maxSpeedKmH = Double.NaN)
        }
        composeRule.onNodeWithTag("speedBadge").assertIsDisplayed()
        composeRule.onNodeWithTag("speedLimitSign").assertDoesNotExist()
    }

    @Test
    fun limitSpaceReservedWhenRequested() {
        // Bottom-anchored placements: the sign slot stays reserved (invisible)
        // so the badge does not shift when the limit sign is hidden.
        composeRule.setContent {
            SpeedWidget(
                currentSpeedKmH = 48.0,
                maxSpeedKmH = Double.NaN,
                reserveLimitSpace = true
            )
        }
        composeRule.onNodeWithTag("speedBadge").assertIsDisplayed()
        composeRule.onNodeWithTag("speedLimitSign").assertDoesNotExist()
        composeRule.onNodeWithTag("speedLimitPlaceholder").assertIsDisplayed()
    }

    @Test
    fun limitSignShownWhenReservingSpace() {
        composeRule.setContent {
            SpeedWidget(
                currentSpeedKmH = 48.0,
                maxSpeedKmH = 50.0,
                reserveLimitSpace = true
            )
        }
        composeRule.onNodeWithTag("speedLimitSign").assertIsDisplayed()
        composeRule.onNodeWithTag("speedLimitPlaceholder").assertDoesNotExist()
    }

    // --- Hidden state ---

    @Test
    fun hiddenWhenSpeedNaN() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = Double.NaN, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
    }

    @Test
    fun hiddenWhenSpeedNegative() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = -1.0, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
    }

    @Test
    fun slotReservedWhenHiddenAndRequested() {
        // Bottom-anchored placements: when no speed data is available the
        // widget slot stays reserved (invisible) so the compass above it does
        // not shift when the widget appears or disappears.
        composeRule.setContent {
            SpeedWidget(
                currentSpeedKmH = Double.NaN,
                maxSpeedKmH = Double.NaN,
                reserveSlotWhenHidden = true
            )
        }
        composeRule.onNodeWithTag("speedWidgetSlot").assertIsDisplayed()
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
        composeRule.onNodeWithTag("speedBadge").assertDoesNotExist()
        composeRule.onNodeWithTag("speedLimitSign").assertDoesNotExist()
    }

    @Test
    fun slotNotReservedByDefault() {
        composeRule.setContent {
            SpeedWidget(currentSpeedKmH = Double.NaN, maxSpeedKmH = 50.0)
        }
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
        composeRule.onNodeWithTag("speedWidgetSlot").assertDoesNotExist()
    }

    // --- Visibility + source selection (screen wiring) ---

    @Test
    fun visibleInFollowModeWithGpsSpeed() {
        val input = speedWidgetInput(
            isNavigating = false,
            navCurrentSpeedKmH = Double.NaN,
            navMaxSpeedKmH = Double.NaN,
            followCurrentSpeedKmH = 48.0,
            followMaxSpeedKmH = 50.0,
            followMode = true
        )
        composeRule.setContent {
            if (input != null) {
                SpeedWidget(input.currentSpeedKmH, input.maxSpeedKmH)
            }
        }
        composeRule.onNodeWithTag("speedWidget").assertIsDisplayed()
        composeRule.onNodeWithText("48 km/h").assertIsDisplayed()
    }

    @Test
    fun visibleDuringNavigationWithEngineSpeed() {
        val input = speedWidgetInput(
            isNavigating = true,
            navCurrentSpeedKmH = 72.0,
            navMaxSpeedKmH = 80.0,
            followCurrentSpeedKmH = Double.NaN,
            followMaxSpeedKmH = Double.NaN,
            followMode = false
        )
        composeRule.setContent {
            if (input != null) {
                SpeedWidget(input.currentSpeedKmH, input.maxSpeedKmH)
            }
        }
        composeRule.onNodeWithTag("speedWidget").assertIsDisplayed()
        composeRule.onNodeWithText("72 km/h").assertIsDisplayed()
    }

    @Test
    fun hiddenWhileBrowsing() {
        val input = speedWidgetInput(
            isNavigating = false,
            navCurrentSpeedKmH = Double.NaN,
            navMaxSpeedKmH = Double.NaN,
            followCurrentSpeedKmH = 48.0,
            followMaxSpeedKmH = 50.0,
            followMode = false
        )
        composeRule.setContent {
            if (input != null) {
                SpeedWidget(input.currentSpeedKmH, input.maxSpeedKmH)
            }
        }
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
    }

    @Test
    fun hiddenInFollowModeWithoutSpeedData() {
        val input = speedWidgetInput(
            isNavigating = false,
            navCurrentSpeedKmH = Double.NaN,
            navMaxSpeedKmH = Double.NaN,
            followCurrentSpeedKmH = Double.NaN,
            followMaxSpeedKmH = Double.NaN,
            followMode = true
        )
        composeRule.setContent {
            if (input != null) {
                SpeedWidget(input.currentSpeedKmH, input.maxSpeedKmH)
            }
        }
        composeRule.onNodeWithTag("speedWidget").assertDoesNotExist()
    }

    @Test
    fun navigationSourceWinsWhileNavigating() {
        val input = speedWidgetInput(
            isNavigating = true,
            navCurrentSpeedKmH = 72.0,
            navMaxSpeedKmH = 80.0,
            followCurrentSpeedKmH = 48.0,
            followMaxSpeedKmH = 50.0,
            followMode = true
        )
        assertEquals(72.0, input!!.currentSpeedKmH, 1e-9)
        assertEquals(80.0, input.maxSpeedKmH, 1e-9)
    }
}
