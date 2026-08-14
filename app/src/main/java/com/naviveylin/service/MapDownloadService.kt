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
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows map download progress"
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
            if (downloadCount > 0) "Downloading $downloadCount map(s)..." else "Downloading map..."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Map Download")
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
        private const val CHANNEL_NAME = "Map Download"
        private const val NOTIFICATION_ID = 1001
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
