package com.naviveylin.auto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.MapStyleLoadReporter
import com.naviveylin.core.stringResolver

/**
 * Reports a rejected stylesheet on the car surfaces.
 *
 * The car templates expose no general message slot (only header and action-strip
 * slots), and adding one would be a template change, so the notice is posted as
 * a one-shot, low-importance notification: it shows the same wording as the
 * phone snackbar (the shared [MapStyleLoadReporter] seam), it is non-blocking,
 * and it cannot interrupt navigation guidance or the surface lifecycle
 * (change `fix-stylesheet-load-crash`, design D6; spec `map-styles` — "Style
 * load failure is visible on both surfaces").
 *
 * @return the message that was handed to the surface, or null when the load
 *         actually succeeded (nothing to report)
 */
internal fun reportCarStyleLoadFailure(
    appContext: Context,
    client: OSMScoutClient,
    requestedStyle: String
): String? {
    val succeeded = runCatching { client.wasLastStyleLoadSuccessful() }.getOrDefault(true)
    val activeStyle = runCatching { client.getActiveStyleSheet() }.getOrNull()

    return MapStyleLoadReporter.reportFailure(
        resolver = appContext.stringResolver(),
        requestedStyle = requestedStyle,
        activeStyle = activeStyle,
        loadSucceeded = succeeded
    )
}

/**
 * Posts the car-side stylesheet-failure notice. The channel is created lazily on
 * the first failure; posting is best-effort (a missing POST_NOTIFICATIONS grant
 * on API 33+ drops the notification instead of failing the session).
 */
internal class CarStyleLoadNotifier(private val appContext: Context) {

    /**
     * Posts [message] as a low-importance notification. Returns whether a
     * notification was handed to the system.
     */
    fun notify(message: String): Boolean {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        return try {
            createChannel(manager)
            manager.notify(NOTIFICATION_ID, buildNotification(message))
            true
        } catch (e: Exception) {
            DiagnosticsLog.log(TAG, "cannot post the map style notification: ${e.message}")
            false
        }
    }

    private fun createChannel(manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.map_style_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = appContext.getString(R.string.map_style_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(message: String) =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.map_style_notification_title))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .build()

    companion object {
        const val TAG = "CarStyleLoadNotifier"
        const val CHANNEL_ID = "map_style"
        const val NOTIFICATION_ID = 1002
    }
}
