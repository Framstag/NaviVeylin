package com.naviveylin.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.naviveylin.MainActivity
import com.naviveylin.R
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NotificationIds
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps a wake lock while maps are being downloaded and
 * shows the download progress in the notification shade.
 */
@AndroidEntryPoint
class MapDownloadService : Service() {

    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        Log.d(TAG, "Service created, wake lock acquired")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action=$action")
        when (action) {
            ACTION_UPDATE -> {
                val count = intent?.getIntExtra(EXTRA_DOWNLOAD_COUNT, 0) ?: 0
                notificationManager.notify(NOTIFICATION_ID, buildNotification(count))
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Platform timeout hook (the form below API 35 has no service type).
     */
    override fun onTimeout(startId: Int) {
        handleForegroundServiceTimeout()
    }

    /**
     * Platform timeout hook (API 35+ includes the foreground service type).
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundServiceTimeout()
    }

    /**
     * The platform ended this foreground service because its type's runtime limit was
     * reached (Android 15+ caps `dataSync` at six hours per 24). Nothing here may report
     * a download as finished: the managers own that state, the partial file stays on
     * disk and the download remains resumable — this only ends the service and the
     * wake lock it holds (spec: map-download-infrastructure — Foreground service for
     * download, Platform timeout ends the service cleanly).
     */
    @androidx.annotation.VisibleForTesting
    internal fun handleForegroundServiceTimeout() {
        Log.w(TAG, "foreground service timeout — stopping, downloads stay resumable")
        DiagnosticsLog.log(TIMEOUT_TAG, "download foreground service timed out; downloads remain resumable")
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        Log.d(TAG, "Service destroyed, wake lock released")
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NaviVeylin:MapDownload").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        // Channel name and description are resources (spec: i18n-l10n —
        // Notification channel name is translatable). Re-applying them on every
        // start updates an existing channel's name, so an install that predates
        // the translation shows the German name without a reinstall.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.map_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.map_download_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(downloadCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText =
            if (downloadCount > 0) {
                resources.getQuantityString(R.plurals.map_downloading_maps, downloadCount, downloadCount)
            } else {
                getString(R.string.map_downloading)
            }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.map_download_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "MapDownloadService"
        private const val CHANNEL_ID = "map_download"

        /** Diagnostics tag for a platform-ended download service (spec: auto-diagnostics). */
        internal const val TIMEOUT_TAG = "DOWNLOAD"

        /** Shared notification identity (spec: navigation-ongoing-notification — Distinct notification identity). */
        private val NOTIFICATION_ID: Int = NotificationIds.MAP_DOWNLOAD

        /**
         * Wake-lock ceiling. Released whenever the service ends — including when the
         * platform ends it ([handleForegroundServiceTimeout]) — so it can never outlive
         * the service it belongs to.
         */
        private const val WAKE_LOCK_TIMEOUT_MS = 14400000L
        const val ACTION_UPDATE = "com.naviveylin.action.UPDATE_DOWNLOAD"
        const val ACTION_STOP = "com.naviveylin.action.STOP_DOWNLOAD"
        const val EXTRA_DOWNLOAD_COUNT = "download_count"

        fun start(context: Context) {
            val intent = Intent(context, MapDownloadService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MapDownloadService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }

        fun update(context: Context, downloadCount: Int) {
            val intent = Intent(context, MapDownloadService::class.java)
            intent.action = ACTION_UPDATE
            intent.putExtra(EXTRA_DOWNLOAD_COUNT, downloadCount)
            context.startService(intent)
        }
    }
}
