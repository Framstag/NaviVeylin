package com.naviveylin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import com.framstag.libosmscout.client.TurnType
import com.naviveylin.R

/**
 * Builds the ongoing driving notification and the car-side turn-by-turn (TBT)
 * contract around it (spec: auto-navigation-hints).
 *
 * Extracted from [NavigationNotificationService] so the notification shape is
 * testable without starting the foreground service: the car host only renders
 * a navigation notification in the rail widget when it is extended with a
 * [CarAppExtender], and the per-surface channel decides whether the platform
 * represents it in the car at all.
 *
 * Each surface has its own tap target: the notification's content intent is the
 * phone target ([android.app.Activity] of the app), while the car extender
 * carries a car-app start request — the host sends the extender's intent when
 * the driver taps the rail widget, and falls back to the phone target when the
 * extender has none (spec: navigation-ongoing-notification — Return to the app
 * from the car rail widget).
 *
 * Pure assembly — the caller supplies the pending intents, the channel and the
 * maneuver artwork; no state is held here.
 */
internal object NavigationNotificationBuilder {

    /** Phone channel (silent, no badge) — unchanged behavior. */
    const val CHANNEL_ID = "navigation"

    /**
     * Android Automotive OS channel: the platform does not represent
     * foreground-service notifications with importance `LOW` or below at all,
     * so the car needs a channel of `IMPORTANCE_DEFAULT`. Heads-up
     * notifications stay off by design (the hint is rail-widget content).
     */
    const val AUTOMOTIVE_CHANNEL_ID = "navigation_automotive"

    /** Channel the notification is posted on for the current device. */
    fun channelIdFor(isAutomotive: Boolean): String =
        if (isAutomotive) AUTOMOTIVE_CHANNEL_ID else CHANNEL_ID

    /**
     * Channel importance for the device: low on the phone, `IMPORTANCE_DEFAULT`
     * on Android Automotive OS. Never `IMPORTANCE_HIGH` — turn hints are not
     * heads-up notifications.
     */
    fun channelImportanceFor(isAutomotive: Boolean): Int =
        if (isAutomotive) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_LOW
        }

    /**
     * Channels to register on the device: the phone channel always (it is the
     * notification's channel on phones and tablets), plus the car channel on
     * automotive hardware.
     */
    fun createChannels(context: Context, isAutomotive: Boolean): List<NotificationChannel> {
        val phone = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.navigation_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.navigation_notification_channel_description)
            setShowBadge(false)
            // Silent by design: guidance is visual-only while driving.
            setSound(null, null)
        }
        val channels = mutableListOf(phone)
        if (isAutomotive) {
            channels += NotificationChannel(
                AUTOMOTIVE_CHANNEL_ID,
                context.getString(R.string.navigation_notification_channel_car_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    context.getString(R.string.navigation_notification_channel_car_description)
                setShowBadge(false)
                setSound(null, null)
            }
        }
        return channels
    }

    /**
     * The ongoing notification.
     *
     * When [hint] is present (NAVIGATION mode) the notification is extended
     * with a [CarAppExtender] carrying the car text roles, the maneuver arrow
     * as large icon, the car tap target and the end-navigation action, which is
     * what makes the car host render it in the rail widget while the app is in
     * the background.
     * When [hint] is null (free driving) the notification is not extended: free
     * driving has no car surface.
     *
     * @param hint car-screen hint content, null when no car hint applies
     * @param channelId channel to post on (see [channelIdFor])
     * @param openIntent phone tap target — the app's phone UI, never a car
     *   surface (a car host cannot show a phone activity)
     * @param carOpenIntent car tap target for the rail widget, null when the
     *   caller has none (the car host then uses [openIntent])
     * @param stopIntent end-navigation action target
     * @param turnBitmap maneuver artwork for the car large icon
     */
    fun build(
        context: Context,
        content: NavigationNotificationContent,
        hint: CarHintContent?,
        channelId: String,
        openIntent: PendingIntent,
        carOpenIntent: PendingIntent?,
        stopIntent: PendingIntent,
        turnBitmap: (TurnType) -> Bitmap
    ): Notification {
        val stopLabel = context.getString(R.string.stop_navigation)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_nav_notification)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(content.bigTextLines.joinToString("\n"))
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (content.showStopAction) {
            builder.addAction(R.drawable.ic_nav_exit, stopLabel, stopIntent)
        }
        if (hint != null) {
            builder.extend(carExtender(context, hint, carOpenIntent, stopIntent, turnBitmap))
        }
        return builder.build()
    }

    /**
     * The car-side extension of the notification: car text roles (maneuver
     * instruction first, unlike the phone's destination-first roles), the
     * maneuver arrow as a large icon, the rail-widget tap target and the
     * end-navigation action. No importance override — the channel decides, and
     * turn hints never raise a heads-up notification.
     *
     * @param carOpenIntent sent when the driver taps the rail widget (or a HUN)
     *   in the car; null falls back to the notification's phone tap target
     */
    fun carExtender(
        context: Context,
        hint: CarHintContent,
        carOpenIntent: PendingIntent?,
        stopIntent: PendingIntent,
        turnBitmap: (TurnType) -> Bitmap
    ): CarAppExtender {
        val extender = CarAppExtender.Builder()
            .setContentTitle(hint.title)
            .setContentText(hint.text)
            .addAction(
                R.drawable.ic_nav_exit,
                context.getString(R.string.stop_navigation),
                stopIntent
            )
        carOpenIntent?.let { extender.setContentIntent(it) }
        hint.turnType?.let { extender.setLargeIcon(turnBitmap(it)) }
        return extender.build()
    }
}
