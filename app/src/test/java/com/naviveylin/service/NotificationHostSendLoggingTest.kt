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

            val entries = DiagnosticsLog.readEntries()
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

            assertEquals(2, DiagnosticsLog.readEntries().size)
        } finally {
            DiagnosticsLog.reset()
        }
    }
}
