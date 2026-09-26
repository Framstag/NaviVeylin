package com.naviveylin.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.RouteInstruction
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.MainActivity
import com.naviveylin.core.ManeuverSymbols
import com.naviveylin.core.NavigationState
import com.naviveylin.core.stringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Tests for the ongoing notification's car-side turn-by-turn contract
 * (spec: auto-navigation-hints — "Turn-by-turn notification contract while
 * navigating", "Notification importance per surface", "End navigation from the
 * car hint", "No car surface for free driving"; spec:
 * navigation-ongoing-notification — "Return to the app from the car rail
 * widget"). Robolectric, default sandbox config (no native library is touched).
 */
@RunWith(RobolectricTestRunner::class)
class NavigationNotificationBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.stringResolver()

    private val instruction = RouteInstruction(
        250.0,
        TurnType.LEFT,
        "Hauptstrasse",
        "Turn left into Hauptstrasse",
        "Turn left"
    )

    private val navigationState = NavigationState(
        isNavigating = true,
        nextInstruction = instruction,
        destinationName = "Home",
        remainingDistance = 12_400.0,
        etaMillis = 1_700_000_000_000L
    )

    /**
     * Sentinel for the phone tap target. Pending intents are cached per request
     * code plus intent, so the helper returns the same object on every call and
     * the assertions below can compare identity.
     */
    private val phoneTapTarget: PendingIntent by lazy { phoneOpenIntent() }

    /** Sentinel for the car tap target the notification service builds. */
    private val carTapTarget: PendingIntent by lazy { carOpenIntent() }

    @Test
    fun navigationNotificationIsExtendedForTheCar() {
        val notification = buildNavigationNotification()

        assertTrue(
            "car host only renders TBT notifications extended with CarAppExtender",
            CarAppExtender.isExtended(notification)
        )
    }

    @Test
    fun carContentUsesCarTextRolesAndManeuverIcon() {
        val extender = CarAppExtender(buildNavigationNotification())

        assertEquals("Turn left", extender.contentTitle)
        assertEquals("250 m · 14:32", extender.contentText)
        assertNotNull("maneuver arrow as car large icon", extender.largeIcon)
    }

    @Test
    fun phoneContentKeepsDestinationFirst() {
        val notification = buildNavigationNotification()

        assertEquals("Home", notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(
            notification.extras.getString(Notification.EXTRA_TEXT)?.contains("Turn left") == true
        )
    }

    @Test
    fun railWidgetTapUsesTheCarTapTarget() {
        val extender = CarAppExtender(buildNavigationNotification())

        assertSame(
            "the host sends the extender's own intent on a rail-widget tap",
            carTapTarget,
            extender.contentIntent
        )
        assertNotSame(
            "the car tap target must not be the phone tap target",
            phoneTapTarget,
            extender.contentIntent
        )
    }

    @Test
    fun phoneTapTargetStaysThePhoneActivity() {
        val notification = buildNavigationNotification()

        assertSame(
            "the phone shade keeps returning to the phone UI",
            phoneTapTarget,
            notification.contentIntent
        )
    }

    @Test
    fun carTapTargetIsAbsentWhenTheCallerHasNone() {
        val notification = buildNavigationNotification(carOpenIntent = null)

        assertTrue(CarAppExtender.isExtended(notification))
        assertNull(
            "no car tap target is invented when the caller supplies none",
            CarAppExtender(notification).contentIntent
        )
    }

    @Test
    fun carHintOffersEndNavigationAction() {
        val extender = CarAppExtender(buildNavigationNotification())

        assertEquals(1, extender.actions.size)
        assertEquals("Stop Navigation", extender.actions.first().title.toString())
    }

    @Test
    fun phoneStopActionCarriesAnIcon() {
        val notification = buildNavigationNotification()

        assertEquals(1, notification.actions.size)
        assertNotNull("stop action icon", notification.actions.first().getIcon())
    }

    @Test
    fun noHeadsUpOverrideIsRequested() {
        val extender = CarAppExtender(buildNavigationNotification())

        assertEquals(NotificationManagerCompat.IMPORTANCE_UNSPECIFIED, extender.importance)
    }

    @Test
    fun freeDrivingNotificationIsNotExtended() {
        val state = NavigationState(isNavigating = false, currentSpeedKmH = 54.0)
        val content = NavigationNotificationContentFormatter.format(state, freeDrivingActive = true)
        val hint = NavigationNotificationContentFormatter.carHint(state, resolver)

        val notification = NavigationNotificationBuilder.build(
            context = context,
            content = content,
            hint = hint,
            channelId = NavigationNotificationBuilder.channelIdFor(isAutomotive = false),
            openIntent = phoneTapTarget,
            carOpenIntent = carTapTarget,
            stopIntent = stopIntent(),
            turnBitmap = ManeuverSymbols::bitmapForTurnType
        )

        assertEquals(null, hint)
        assertFalse(
            "free driving has no car surface",
            CarAppExtender.isExtended(notification)
        )
        assertSame(
            "free driving keeps the phone-only tap behavior",
            phoneTapTarget,
            notification.contentIntent
        )
        assertTrue("no action without a driving mode", notification.actions?.isEmpty() != false)
    }

    @Test
    fun phoneChannelStaysSilent() {
        val channels = NavigationNotificationBuilder.createChannels(context, isAutomotive = false)

        assertEquals(
            listOf(NavigationNotificationBuilder.CHANNEL_ID),
            channels.map { it.id }
        )
        assertEquals(NotificationManager.IMPORTANCE_LOW, channels.single().importance)
        assertEquals(
            NavigationNotificationBuilder.CHANNEL_ID,
            NavigationNotificationBuilder.channelIdFor(isAutomotive = false)
        )
    }

    @Test
    fun automotiveAddsACarRepresentableChannel() {
        val channels = NavigationNotificationBuilder.createChannels(context, isAutomotive = true)
        val carChannel = channels.first { it.id == NavigationNotificationBuilder.AUTOMOTIVE_CHANNEL_ID }

        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            NavigationNotificationBuilder.channelImportanceFor(isAutomotive = true)
        )
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, carChannel.importance)
        assertEquals(
            NavigationNotificationBuilder.AUTOMOTIVE_CHANNEL_ID,
            NavigationNotificationBuilder.channelIdFor(isAutomotive = true)
        )
    }

    @Test
    fun noChannelRequestsHeadsUpImportance() {
        val channels = NavigationNotificationBuilder.createChannels(context, isAutomotive = true) +
            NavigationNotificationBuilder.createChannels(context, isAutomotive = false)

        assertTrue(
            "turn hints are rail-widget content, never heads-up notifications",
            channels.none { it.importance >= NotificationManager.IMPORTANCE_HIGH }
        )
    }

    private fun buildNavigationNotification(
        openIntent: PendingIntent = phoneTapTarget,
        carOpenIntent: PendingIntent? = carTapTarget
    ): Notification {
        val content =
            NavigationNotificationContentFormatter.format(navigationState, freeDrivingActive = false)
        val hint = NavigationNotificationContentFormatter.carHint(
            navigationState,
            resolver,
            etaClock = { "14:32" },
            locale = Locale.US
        )
        return NavigationNotificationBuilder.build(
            context = context,
            content = content,
            hint = hint,
            channelId = NavigationNotificationBuilder.channelIdFor(isAutomotive = false),
            openIntent = openIntent,
            carOpenIntent = carOpenIntent,
            stopIntent = stopIntent(),
            turnBitmap = ManeuverSymbols::bitmapForTurnType
        )
    }

    private fun phoneOpenIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    /** Mirrors the shape the service supplies: a broadcast car-app start request. */
    private fun carOpenIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        2,
        Intent(CAR_TAP_ACTION),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, NavigationNotificationService::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val CAR_TAP_ACTION = "com.naviveylin.test.CAR_TAP"
    }
}
