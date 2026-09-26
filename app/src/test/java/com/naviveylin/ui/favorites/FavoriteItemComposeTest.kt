package com.naviveylin.ui.favorites

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
 * Compose tests for the favorites sheet's row subtitle (spec `fav-management-ui`
 * — Group row shows an unambiguous coordinate pair; spec `i18n-l10n` — Coordinate
 * string is locale-stable).
 *
 * Kept out of `FavoritesSheetReorderComposeTest` (the class with the documented
 * load-sensitive Compose timeout) so this contract has a stable home.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteItemComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val originalDefaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    private fun launch(favorite: FavoriteLocation) {
        composeRule.setContent {
            FavoriteItem(
                favorite = favorite,
                isStarred = false,
                onClick = {},
                onDelete = {},
                onRename = {},
                onToggleStar = {}
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun rowShowsLocaleStablePairOnAGermanDevice() {
        Locale.setDefault(Locale.GERMANY)

        launch(FavoriteLocation("Home", 51.5, 7.4))

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("51.50000, 7.40000").assertIsDisplayed()
        composeRule.onNodeWithText("51,50000, 7,40000").assertDoesNotExist()
    }

    @Test
    fun rowShowsTheSamePairOnAnEnglishDevice() {
        Locale.setDefault(Locale.US)

        launch(FavoriteLocation("Home", 51.5, 7.4))

        composeRule.onNodeWithText("51.50000, 7.40000").assertIsDisplayed()
    }
}
