package com.naviveylin.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.naviveylin.BuildConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI test for the about dialog version display.
 * NOTE: must run under the DEFAULT Robolectric sandbox (no @Config(sdk=...)
 * or @GraphicsMode) so the JNI stub .so loads in the shared classloader —
 * see AGENTS.md classloader rule.
 */
@RunWith(RobolectricTestRunner::class)
class AboutDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogShowsVersionFromBuildConfig() {
        composeRule.setContent {
            AboutDialog(onDismiss = {})
        }
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }
}
