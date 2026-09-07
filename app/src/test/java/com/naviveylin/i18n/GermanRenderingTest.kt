package com.naviveylin.i18n

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.naviveylin.BuildConfig
import com.naviveylin.ui.about.AboutDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * German rendering verification (spec: German is fully supported). Runs with
 * German resource qualifiers; AboutDialog has no native dependencies, so a
 * non-default sandbox is safe (see AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "de")
class GermanRenderingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutDialogRendersGerman() {
        composeRule.setContent {
            AboutDialog(onDismiss = {})
        }
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeRule.onNodeWithText("Kartendaten © OpenStreetMap-Mitwirkende", substring = true).assertExists()
    }
}
