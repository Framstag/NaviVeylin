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
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.ManeuverSymbols
import com.naviveylin.core.NavigationStopRequests
import com.naviveylin.core.NotificationIds
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
        val hint = carHintFor(state)
        // The foreground post is unconditional (the FGS contract), but it also seeds
        // the dedup baseline for the observer's later posts.
        // The foreground call is refused by the platform in one documented case (a
        // location-typed FGS started while the app is backgrounded without an eligible
        // state): degrade to stopping the service instead of dying
        // (spec: car-host-fault-isolation — No fault escapes into the host path).
        val started = runGuardedNotification("startForeground") {
            startForeground(NOTIFICATION_ID, buildNotification(content, hint))
        }
        if (!started) {
            stopSelf()
            return
        }
        lastPost = NotificationPost(content, hint)
        recordNotificationPost(lastPost!!)
        observeDrivingState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Logged so a device run can tell "action delivered" from "wrong stop
        // target" (the failure mode recorded in TODO.md §46).
        Log.d(TAG, "onStartCommand action=${intent?.action ?: "-"} startId=$startId")
        handleAction(intent?.action, stateProvider)
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
        val post = NotificationPost(content, hint)
        // Re-post only when the host-visible content changed (spec:
        // car-host-fault-isolation — Bounded host-facing traffic while not visible):
        // the state flow emits at the position/speed rate (~1 Hz), and re-posting
        // identical content makes the host re-render its rail widget for nothing.
        if (!NavigationNotificationContentFormatter.hostVisibleContentChanged(lastPost, post)) return
        // A failed rebuild/post must not kill the service that carries the driving
        // session (spec: car-host-fault-isolation — No fault escapes into the host path).
        val posted = runGuardedNotification("notification post") {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content, hint))
        }
        // Baseline moves only on a successful post, so a dropped one is retried on the
        // next emission instead of being deduped away.
        if (posted) {
            lastPost = post
            recordNotificationPost(post)
        }
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

    /**
     * Baseline for the content dedup (spec: car-host-fault-isolation — Bounded
     * host-facing traffic while not visible): the last content the host actually saw.
     */
    private var lastPost: NotificationPost? = null

    private lateinit var notificationManager: NotificationManager

    /** True on Android Automotive OS hardware; decides the notification channel. */
    private var isAutomotive: Boolean = false

    /** Channel the ongoing notification is posted on (see [NavigationNotificationBuilder]). */
    private lateinit var channelId: String

    companion object {
        private const val TAG = "NavigationNotificationService"

        /**
         * Notification identity of the ongoing notification: the foreground-service
         * notification and the carrier of the car rail-widget turn hint. Shared so no other
         * notice can post on it (spec: navigation-ongoing-notification — Distinct
         * notification identity).
         */
        private val NOTIFICATION_ID: Int = NotificationIds.NAVIGATION_ONGOING
        const val ACTION_START = "com.naviveylin.action.START_NAV_NOTIFICATION"
        const val ACTION_STOP = "com.naviveylin.action.STOP_NAV_NOTIFICATION"
        const val ACTION_STOP_NAVIGATION = "com.naviveylin.action.STOP_NAVIGATION"

        /**
         * Route a start-command action. Pure and testable without the service
         * lifecycle: [ACTION_STOP_NAVIGATION] (the shade's stop action, phone and
         * car extender alike) broadcasts a stop request — whichever controller is
         * navigating stops itself, and an idle one does nothing. Any other action
         * (including [ACTION_START] and [ACTION_STOP], which are handled by the
         * driving-state observer) is ignored.
         */
        internal fun handleAction(action: String?, stopRequests: NavigationStopRequests) {
            if (action == ACTION_STOP_NAVIGATION) {
                stopRequests.requestStop()
            }
        }
    }
}

/**
 * Runs one car-facing notification action, confining a fault to it (spec:
 * car-host-fault-isolation — No fault escapes into the host path; design D3). The
 * notification is built from library validators and posted to the platform, both of
 * which can throw (`SecurityException`/`ForegroundServiceStartNotAllowedException` for
 * a refused foreground start, `IllegalArgumentException` for an unserializable
 * payload); an escape from the service's `onCreate` would kill the driving session's
 * process.
 *
 * @return true when [block] completed, false when it threw (logged)
 */
internal fun runGuardedNotification(why: String, block: () -> Unit): Boolean = try {
    block()
    true
} catch (t: Throwable) {
    Log.w("NavigationNotificationService", "$why failed — degraded", t)
    false
}

/** Diagnostics tag for what the app sent the car host. */
internal const val HOST_TAG = "HOST"

/**
 * Record a notification post (spec: car-host-fault-isolation — Host interaction is
 * diagnosable): the car host renders its rail widget from this notification, so
 * correlating a host failure needs the posts with the content that triggered them. A
 * deduped (unchanged) emission never reaches here — it is dropped before the post.
 */
internal fun recordNotificationPost(post: NotificationPost) {
    DiagnosticsLog.log(
        HOST_TAG,
        "NOTIF post title='${post.content.title}' text='${post.content.contentText}' " +
            "hint='${post.hint?.title ?: "-"}'"
    )
}
