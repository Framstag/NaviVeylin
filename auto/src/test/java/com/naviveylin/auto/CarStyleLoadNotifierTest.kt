package com.naviveylin.auto

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeStyleLoadClient
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.NotificationIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests for the car-side stylesheet-failure report (task 3.2 of change
 * `fix-stylesheet-load-crash`): the failure is reported with the shared :core
 * wording and the presentation is a notification, so nothing in the car
 * template or the session is touched.
 *
 * Robolectric default sandbox: the client fake needs the JNI stub (see
 * `AGENTS.md` — do not add a sandbox config here).
 */
@RunWith(RobolectricTestRunner::class)
class CarStyleLoadNotifierTest {

    private lateinit var context: Context
    private lateinit var client: OSMScoutClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeStyleLoadClient()
    }

    @Test
    fun failureReportsTheSharedWording() {
        (client as FakeStyleLoadClient).loadSuccessful = false
        (client as FakeStyleLoadClient).activeStyle = "standard.oss"

        val message = reportCarStyleLoadFailure(context, client, "cycle")

        assertEquals("Map style \"cycle\" could not be loaded — still using \"standard.oss\"", message)
    }

    @Test
    fun successfulLoadReportsNothing() {
        (client as FakeStyleLoadClient).loadSuccessful = true

        assertNull(reportCarStyleLoadFailure(context, client, "cycle"))
    }

    @Test
    fun noticeIsPostedWithTheMessageAndALowImportanceChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertTrue(CarStyleLoadNotifier(context).notify("Map style \"cycle\" could not be loaded"))

        val channel = manager.notificationChannels.firstOrNull { it.id == CarStyleLoadNotifier.CHANNEL_ID }
        assertNotNull("the channel must exist after the first notice", channel)
        assertEquals(
            "a low-importance channel is non-blocking",
            NotificationManager.IMPORTANCE_LOW,
            channel!!.importance
        )

        val posted = shadowOf(manager).getNotification(CarStyleLoadNotifier.NOTIFICATION_ID)
        assertNotNull("the notice must be posted", posted)
        assertEquals(
            context.getString(R.string.map_style_notification_title),
            posted.extras.getString("android.title")
        )
        assertEquals("Map style \"cycle\" could not be loaded", posted.extras.getString("android.text"))
    }

    @Test
    fun noticeIsSilentSoGuidanceIsNotInterrupted() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        CarStyleLoadNotifier(context).notify("Map style \"cycle\" could not be loaded")

        val posted = shadowOf(manager).getNotification(CarStyleLoadNotifier.NOTIFICATION_ID)
        assertEquals("no sound/vibration", 0, posted.defaults)
    }

    @Test
    fun theNoticeNeverPostsOnTheOngoingNavigationIdentity() {
        // The ongoing navigation notification is the foreground-service notification and the
        // carrier of the car rail-widget turn hint: a notice on that identity takes the hint
        // off the rail (spec: navigation-ongoing-notification — Distinct notification identity).
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotEquals(NotificationIds.NAVIGATION_ONGOING, CarStyleLoadNotifier.NOTIFICATION_ID)

        CarStyleLoadNotifier(context).notify("Map style \"cycle\" could not be loaded")

        assertNotNull(
            "the notice is posted under its own identity",
            shadowOf(manager).getNotification(NotificationIds.MAP_STYLE_NOTICE)
        )
        assertNull(
            "nothing is posted under the navigation identity",
            shadowOf(manager).getNotification(NotificationIds.NAVIGATION_ONGOING)
        )
    }
}
