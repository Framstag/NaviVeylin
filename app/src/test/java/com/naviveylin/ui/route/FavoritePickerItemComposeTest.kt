package com.naviveylin.ui.route

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.framstag.libosmscout.client.FavoriteLocation
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Compose tests for the route panel's favorite picker row (spec `i18n-l10n` —
 * Coordinate string is locale-stable): its subtitle went through the device
 * locale, so a German device showed the ambiguous pair "51,50000, 7,40000".
 *
 * Tests the row composable directly instead of the surrounding
 * `ModalBottomSheet`, so the contract does not depend on the sheet's window in
 * Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class FavoritePickerItemComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val originalDefaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    private fun launch(favorite: FavoriteLocation) {
        composeRule.setContent {
            FavoriteItem(fav = favorite, onClick = {})
        }
        composeRule.waitForIdle()
    }

    @Test
    fun rowShowsLocaleStablePairOnAGermanDevice() {
        Locale.setDefault(Locale.GERMANY)

        launch(FavoriteLocation("Hotel Central", 51.5, 7.4))

        composeRule.onNodeWithText("Hotel Central").assertIsDisplayed()
        composeRule.onNodeWithText("51.50000, 7.40000").assertIsDisplayed()
        composeRule.onNodeWithText("51,50000, 7,40000").assertDoesNotExist()
    }
}
