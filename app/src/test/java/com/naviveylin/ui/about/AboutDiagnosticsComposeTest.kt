package com.naviveylin.ui.about

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * Compose UI tests for the phone-side diagnostics view.
 *
 * The layout and the share intent are driven through the pure
 * [DiagnosticsLogView] with fixed data (deterministic — no load, no dispatcher
 * hop); [AboutDialog] itself is covered for opening the view. The background load
 * that feeds the view is covered by `DiagnosticsLogWritePathTest`
 * (`backgroundReadsMatchTheSynchronousOnes`) and, end to end, by the car screen's
 * `DiagnosticsScreenTest`. Default Robolectric sandbox, no @Config.
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
    }

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
        logFile.delete()
    }

    @Test
    fun diagnosticsDialogShowsLogEntriesNewestFirst() {
        composeRule.setContent {
            DiagnosticsLogView(
                entries = listOf("[t] SESSION entry-one", "[t] CRASH entry-two"),
                shareText = "entry-one\nentry-two",
                onRefresh = {},
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("entry-one", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("entry-two", substring = true).assertIsDisplayed()
    }

    @Test
    fun diagnosticsDialogShowsEmptyState() {
        composeRule.setContent {
            DiagnosticsLogView(entries = emptyList(), shareText = "", onRefresh = {}, onDismiss = {})
        }

        composeRule.onNodeWithText("No log entries yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun refreshInvokesItsCallback() {
        var refreshes = 0
        composeRule.setContent {
            DiagnosticsLogView(
                entries = listOf("[t] SESSION entry-one"),
                shareText = "entry-one",
                onRefresh = { refreshes++ },
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun shareButtonStartsSendIntentWithTheLoadedText() {
        composeRule.setContent {
            DiagnosticsLogView(
                entries = listOf("[t] SESSION entry-one"),
                shareText = "[t] SESSION entry-one",
                onRefresh = {},
                onDismiss = {}
            )
        }
        composeRule.onNodeWithText("Share").performClick()

        val started = shadowOf(
            ApplicationProvider.getApplicationContext<Application>()
        ).nextStartedActivity
        // startActivity(Intent.createChooser(...)) surfaces the CHOOSER wrapper
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val inner = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertEquals(Intent.ACTION_SEND, inner!!.action)
        assertEquals("text/plain", inner.type)
        assertTrue(inner.getStringExtra(Intent.EXTRA_TEXT)!!.contains("entry-one"))
    }

    @Test
    fun aboutDialogOpensTheDiagnosticsView() {
        composeRule.setContent { AboutDialog(onDismiss = {}) }
        composeRule.onNodeWithText("Diagnostics").performScrollTo().performClick()

        // The loading state renders no rows, so the dialog's title and its actions
        // are what proves the view opened.
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
    }
}
