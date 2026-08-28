package com.naviveylin.ui.about

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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

    @Test
    fun dialogShowsOsmLicenceLink() {
        composeRule.setContent {
            AboutDialog(onDismiss = {})
        }
        composeRule.onNodeWithText("Open Database License", substring = true).assertExists()
        composeRule.onNodeWithText("openstreetmap.org/copyright").assertExists()
    }

    @Test
    fun osmLicenceLinkOpensCopyrightPage() {
        composeRule.setContent {
            AboutDialog(onDismiss = {})
        }
        composeRule.onNodeWithText("openstreetmap.org/copyright").performScrollTo().performClick()

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.openstreetmap.org/copyright", started.dataString)
    }
}
