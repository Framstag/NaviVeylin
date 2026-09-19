package com.naviveylin.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.naviveylin.AutomotiveDevice
import com.naviveylin.MainActivity
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.ManeuverSymbols
import com.naviveylin.core.stringResolver
import com.naviveylin.navigation.NavigationStateProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.car.app.activity.CarAppActivity

/**
 * Foreground service (type `location`) carrying the ongoing navigation /
 * free-driving notification while a driving mode is active (spec:
 * navigation-ongoing-notification).
 *
 * Owned by [com.naviveylin.navigation.NavigationNotificationController];
 * this service is a rendering shell: it injects the same singleton state
 * flows, re-renders the notification on every emission, and stops itself
 * when the driving state clears (belt-and-braces with the controller's
 * ACTION_STOP). [START_NOT_STICKY]: a killed process is never resurrected
 * with a lying "still navigating" notification — the in-process navigation
 * state is gone with it (spec R2).
 */
@AndroidEntryPoint
class NavigationNotificationService : Service() {

    @Inject
    lateinit var stateProvider: NavigationStateProvider

    @Inject
    lateinit var drivingModeProvider: DrivingModeProvider

    private var scope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        isAutomotive = AutomotiveDevice.isAutomotive(this)
        channelId = NavigationNotificationBuilder.channelIdFor(isAutomotive)
        NavigationNotificationBuilder.createChannels(this, isAutomotive)
            .forEach(notificationManager::createNotificationChannel)
        // FGS contract: must post the foreground notification within 5s of
        // the startForegroundService call — render the current state now and
        // keep re-rendering from the observer.
        val state = stateProvider.state.value
        val freeDriving = drivingModeProvider.freeDrivingActive.value
        val content = NavigationNotificationContentFormatter.format(state, freeDriving)
        startForeground(NOTIFICATION_ID, buildNotification(content, carHintFor(state)))
        observeDrivingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_NAVIGATION -> {
                // Stop action from the notification shade: end the route.
                // stateProvider mirrors stopNavigation() to the active
                // navigation controller (phone VM or car controller).
                stateProvider.stopNavigation()
            }
        }
        // The observer in onCreate stops the service when the driving state
        // clears; a re-delivered start with no driving state also self-stops.
        if (!drivingStateActive()) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun drivingStateActive(): Boolean =
        stateProvider.state.value.isNavigating || drivingModeProvider.freeDrivingActive.value

    /** Collect the shared driving state and render the notification live. */
    private fun observeDrivingState() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        this.scope = scope
        scope.launch {
            combine(
                stateProvider.state,
                drivingModeProvider.freeDrivingActive
            ) { navState, freeDriving -> Pair(navState, freeDriving) }
                .collect { (navState, freeDriving) ->
                    val active = navState.isNavigating || freeDriving
                    if (!active) {
                        Log.d(TAG, "Driving state cleared — stopping service")
                        stopSelf()
                        return@collect
                    }
                    render(navState, freeDriving)
                }
        }
    }

    private fun render(navState: com.naviveylin.core.NavigationState, freeDriving: Boolean) {
        val content = NavigationNotificationContentFormatter.format(navState, freeDriving)
        val hint = carHintFor(navState)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(content, hint))
    }

    /**
     * Car-screen hint for the current state, or null when no car hint applies
     * (free driving — spec: auto-navigation-hints, "No car surface for free
     * driving"). Null means the notification is not extended for the car host.
     */
    private fun carHintFor(navState: com.naviveylin.core.NavigationState): CarHintContent? =
        NavigationNotificationContentFormatter.carHint(navState, stringResolver())

    private fun buildNotification(
        content: NavigationNotificationContent,
        hint: CarHintContent?
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, openTargetActivity()).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NavigationNotificationService::class.java).apply {
                action = ACTION_STOP_NAVIGATION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NavigationNotificationBuilder.build(
            context = this,
            content = content,
            hint = hint,
            channelId = channelId,
            openIntent = openIntent,
            stopIntent = stopIntent,
            turnBitmap = ManeuverSymbols::bitmapForTurnType
        )
    }

    /** Phone → MainActivity; automotive → the car template host activity. */
    private fun openTargetActivity(): Class<*> {
        return if (isAutomotive) {
            CarAppActivity::class.java
        } else {
            MainActivity::class.java
        }
    }

    private lateinit var notificationManager: NotificationManager

    /** True on Android Automotive OS hardware; decides the notification channel. */
    private var isAutomotive: Boolean = false

    /** Channel the ongoing notification is posted on (see [NavigationNotificationBuilder]). */
    private lateinit var channelId: String

    companion object {
        private const val TAG = "NavigationNotificationService"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.naviveylin.action.START_NAV_NOTIFICATION"
        const val ACTION_STOP = "com.naviveylin.action.STOP_NAV_NOTIFICATION"
        const val ACTION_STOP_NAVIGATION = "com.naviveylin.action.STOP_NAVIGATION"
    }
}
