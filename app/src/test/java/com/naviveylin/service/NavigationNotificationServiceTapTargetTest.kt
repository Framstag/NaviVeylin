package com.naviveylin.service

import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.activity.CarAppActivity
import androidx.car.app.notification.CarAppNotificationBroadcastReceiver
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Tests for the car tap target of the ongoing notification (spec:
 * navigation-ongoing-notification — "Return to the app from the car rail
 * widget"). The rail-widget tap must reach the app as a car-app start request;
 * a phone activity intent cannot be shown by a car host, which is why the
 * target is platform-specific.
 *
 * Robolectric, default sandbox config (no native library is touched). The target
 * is read back through [org.robolectric.shadows.ShadowPendingIntent] instead of
 * being sent: the delivered intent is the host's business, and the library's
 * component extra is package-private.
 */
@RunWith(RobolectricTestRunner::class)
class NavigationNotificationServiceTapTargetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun carTapTargetIsAHostAnsweredBroadcast() {
        val pendingIntent = NavigationNotificationService.carOpenIntent(context)
        val shadow = shadowOf(pendingIntent)

        assertTrue(
            "projection: the tap is a broadcast the host answers with startCarApp",
            shadow.isBroadcastIntent()
        )
        assertFalse(
            "the host has to add its start-car-app extras, so the intent cannot be immutable",
            shadow.isImmutable()
        )
        assertEquals(context.packageName, pendingIntent.creatorPackage)

        val saved = requireNotNull(shadow.savedIntent) { "no intent recorded for the tap target" }
        assertEquals(
            "the app owns the receiver that forwards the tap to the host",
            CarAppNotificationBroadcastReceiver::class.java.name,
            saved.component?.className
        )
        assertEquals(context.packageName, saved.component?.packageName)
    }

    @Test
    fun automotiveTapTargetLaunchesTheCarAppActivity() {
        shadowOf(context.packageManager).setSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
            true
        )

        val pendingIntent = NavigationNotificationService.carOpenIntent(context)
        val shadow = shadowOf(pendingIntent)

        assertTrue(
            "Android Automotive OS: the tap launches the template host activity",
            shadow.isActivityIntent()
        )
        assertEquals(
            CarAppActivity::class.java.name,
            shadow.savedIntent?.component?.className
        )
    }
}
