package com.naviveylin.ui.about

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Compose UI tests for the phone-side diagnostics view (task 3.2):
 * log entries rendered in the dialog and the share intent.
 * Default Robolectric sandbox, no @Config.
 */
@RunWith(RobolectricTestRunner::class)
class AboutDiagnosticsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var logFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        logFile = File(context.filesDir, "diagnostics/dialog-test.log")
        logFile.parentFile?.mkdirs()
        logFile.delete()
        DiagnosticsLog.initForTest(logFile)
        DiagnosticsLog.log("SESSION", "entry-one")
        DiagnosticsLog.log("CRASH", "entry-two")
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
    }

    @Test
    fun diagnosticsDialogShowsLogEntries() {
        composeRule.setContent { AboutDialog(onDismiss = {}) }
        composeRule.onNodeWithText("Diagnostics").performClick()

        composeRule.onNodeWithText("entry-one", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("entry-two", substring = true).assertIsDisplayed()
    }

    @Test
    fun shareButtonStartsSendIntent() {
        composeRule.setContent { AboutDialog(onDismiss = {}) }
        composeRule.onNodeWithText("Diagnostics").performClick()
        composeRule.onNodeWithText("Share").performClick()

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        // startActivity(Intent.createChooser(...)) surfaces the CHOOSER wrapper
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, inner!!.action)
        assertEquals("text/plain", inner.type)
        assertTrue(inner.getStringExtra(Intent.EXTRA_TEXT)!!.contains("entry-one"))
    }
}
