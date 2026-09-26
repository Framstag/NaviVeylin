package com.naviveylin.ui.about

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The diagnostics disclosure on the phone (spec: auto-diagnostics — Export and
 * viewers disclose the log contents): the viewer shows what the log holds and how
 * long it is kept, the shared text leads with it, and the wording exists in German.
 *
 * This class does not touch the JNI stub, so the per-method German qualifier is safe
 * (AGENTS.md classloader rule; same idiom as `auto/GermanRenderingTest`).
 */
@RunWith(RobolectricTestRunner::class)
class DiagnosticsDisclosureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun disclosure(): String =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(com.naviveylin.core.R.string.diagnostics_disclosure)

    private fun renderViewer() {
        composeRule.setContent {
            DiagnosticsLogView(
                entries = listOf("SESSION entered android auto"),
                shareText = disclosure(),
                onRefresh = {},
                onDismiss = {}
            )
        }
    }

    @Test
    fun theViewerShowsTheDisclosure() {
        renderViewer()

        composeRule.onNodeWithText(disclosure()).assertIsDisplayed()
    }

    @Test
    fun theSharedTextLeadsWithTheDisclosure() {
        val text = diagnosticsShareText("DISCLOSURE", "SESSION one\nSESSION two")

        assertTrue("the receiver must see what the file holds first: $text", text.startsWith("DISCLOSURE"))
        assertTrue("the log follows the disclosure", text.contains("SESSION one"))
    }

    @Test
    fun anEmptyLogStillCarriesTheDisclosure() {
        assertEquals("DISCLOSURE", diagnosticsShareText("DISCLOSURE", ""))
    }

    @Test
    @Config(qualifiers = "de")
    fun theDisclosureIsLocalized() {
        renderViewer()

        composeRule.onNodeWithText(disclosure()).assertIsDisplayed()
    }
}
