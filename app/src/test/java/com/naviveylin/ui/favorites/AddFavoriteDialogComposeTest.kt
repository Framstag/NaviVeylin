package com.naviveylin.ui.favorites

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Compose tests for the add-favorite coordinate entry (spec `fav-management-ui`
 * — Coordinate entry accepts either decimal separator; spec `i18n-l10n` —
 * Coordinate input accepts the locale decimal separator).
 *
 * This is the regression test for `TODO.md` §45: on a German device the dialog
 * prefilled `51,51391` and its Save button gated on `toDoubleOrNull()`, so the
 * button never became usable and the favourite could not be added at all.
 *
 * Runs under the default Robolectric sandbox: the dialog itself needs no JNI,
 * but the class deliberately stays on the default config like its sibling
 * `FavoritesSheetReorderComposeTest` (guidelines/Design.md §11).
 */
@RunWith(RobolectricTestRunner::class)
class AddFavoriteDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var confirmed: Triple<String, Double, Double>? = null

    private val originalDefaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale)
    }

    private fun launch(initialLat: Double, initialLon: Double) {
        confirmed = null
        composeRule.setContent {
            AddFavoriteDialog(
                groupName = "Favorites",
                initialLat = initialLat,
                initialLon = initialLon,
                onConfirm = { name, lat, lon -> confirmed = Triple(name, lat, lon) },
                onDismiss = {}
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * The dialog's three input fields in composition order: name, latitude,
     * longitude. Addressing them by index keeps the test independent of the
     * localized labels.
     */
    private fun nameField() = composeRule.onAllNodes(hasSetTextAction())[0]

    private fun latitudeField() = composeRule.onAllNodes(hasSetTextAction())[1]

    private fun longitudeField() = composeRule.onAllNodes(hasSetTextAction())[2]

    private fun saveButton() = composeRule.onNodeWithText(context.getString(R.string.save))

    @Test
    fun prefilledMapCenterCanBeSavedUnchangedOnAGermanDevice() {
        Locale.setDefault(Locale.GERMANY)

        launch(initialLat = 51.51391, initialLon = 7.47434)

        // The prefill is locale-stable, so it can be parsed back verbatim.
        composeRule.onNodeWithText("51.51391").assertIsDisplayed()
        composeRule.onNodeWithText("7.47434").assertIsDisplayed()
        composeRule.onNodeWithText("51,51391").assertDoesNotExist()

        nameField().performTextInput("Home")

        saveButton().assertIsEnabled()
        saveButton().performClick()

        assertEquals(Triple("Home", 51.51391, 7.47434), confirmed)
    }

    @Test
    fun commaDecimalSeparatorIsAccepted() {
        launch(initialLat = 0.0, initialLon = 0.0)

        nameField().performTextInput("Home")
        latitudeField().performTextInput("51,51391")
        longitudeField().performTextInput("7,47434")

        saveButton().assertIsEnabled()
        saveButton().performClick()

        assertEquals(Triple("Home", 51.51391, 7.47434), confirmed)
    }

    @Test
    fun dotDecimalSeparatorIsAccepted() {
        launch(initialLat = 0.0, initialLon = 0.0)

        nameField().performTextInput("Hotel")
        latitudeField().performTextInput("51.51391")
        longitudeField().performTextInput("7.47434")

        saveButton().assertIsEnabled()
        saveButton().performClick()

        assertEquals(Triple("Hotel", 51.51391, 7.47434), confirmed)
    }

    @Test
    fun invalidCoordinateTextKeepsSaveUnavailable() {
        launch(initialLat = 0.0, initialLon = 0.0)

        nameField().performTextInput("Home")
        latitudeField().performTextInput("51,51,391")
        longitudeField().performTextInput("7,47434")

        saveButton().assertIsNotEnabled()
        assertNull(confirmed)
    }

    @Test
    fun outOfRangeLatitudeKeepsSaveUnavailable() {
        launch(initialLat = 0.0, initialLon = 0.0)

        nameField().performTextInput("Home")
        latitudeField().performTextInput("91")
        longitudeField().performTextInput("7,47434")

        saveButton().assertIsNotEnabled()

        // The same field accepts a value inside the range again.
        latitudeField().performTextClearance()
        latitudeField().performTextInput("51,5")

        saveButton().assertIsEnabled()
    }

    @Test
    fun nameIsRequiredBeforeSave() {
        launch(initialLat = 51.51391, initialLon = 7.47434)

        saveButton().assertIsNotEnabled()

        nameField().performTextInput("Home")

        saveButton().assertIsEnabled()
    }
}
