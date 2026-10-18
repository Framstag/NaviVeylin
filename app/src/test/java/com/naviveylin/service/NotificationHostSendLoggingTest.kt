package com.naviveylin.service

import com.naviveylin.core.DiagnosticsLog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Host-send logging (spec: car-host-fault-isolation — Host interaction is diagnosable):
 * the notification is the car host's rail-widget input, so a host failure needs the
 * posts with the content that triggered them. A deduped (unchanged) emission is dropped
 * before the post (`NavigationNotificationContentFormatter.hostVisibleContentChanged`,
 * see `NavigationNotificationContentFormatterTest`), so it records nothing.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationHostSendLoggingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun logFile(): File = File(tempFolder.root, DiagnosticsLog.LOG_FILE)

    private fun post(title: String, text: String) = NotificationPost(
        content = NavigationNotificationContent(
            title = title,
            contentText = text,
            bigTextLines = emptyList(),
            showStopAction = true
        ),
        hint = CarHintContent(title = "Turn left", text = "250 m · 13:00", turnType = null)
    )

    @Test
    fun aPostIsRecordedWithItsContentAndTimestamp() {
        DiagnosticsLog.initForTest(logFile())
        try {
            recordNotificationPost(post("Home", "Turn left into Hauptstrasse"))
            // The file a case installs is the process-wide sink: work another class left running logs into
            // it while this case holds it (measured 2026-10-11, `fix-browse-recenter-dwell`, issue #115).
            DiagnosticsLog.log("OTHER", "a line another class logged into this file: lat=52.520008 lon=13.404954")

            // Only the entries this case's own subject wrote are its evidence (issue #115): the file is the
            // process-wide sink, so a line another class logs while this case holds it is not a post.
            val entries = DiagnosticsLog.readEntries().filter { it.contains("HOST NOTIF post") }
            assertEquals(1, entries.size)
            assertTrue("timestamped", entries.single().startsWith("["))
            assertTrue("tagged", entries.single().contains("HOST NOTIF post"))
            assertTrue("with the changed content", entries.single().contains("title='Home'"))
            assertTrue("with the car hint", entries.single().contains("hint='Turn left'"))
        } finally {
            DiagnosticsLog.reset()
        }
    }

    @Test
    fun everyPostAddsExactlyOneEntry() {
        DiagnosticsLog.initForTest(logFile())
        try {
            recordNotificationPost(post("Home", "Turn left"))
            recordNotificationPost(post("Home", "Turn right"))
            DiagnosticsLog.log("OTHER", "a line another class logged into this file: lat=52.520008 lon=13.404954")

            assertEquals(2, DiagnosticsLog.readEntries().count { it.contains("HOST NOTIF post") })
        } finally {
            DiagnosticsLog.reset()
        }
    }
}
