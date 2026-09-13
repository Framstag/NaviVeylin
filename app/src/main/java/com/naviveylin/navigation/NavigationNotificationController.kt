package com.naviveylin.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.naviveylin.core.DrivingModeProvider
import com.naviveylin.core.NavigationState
import com.naviveylin.service.NavigationNotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Starts and stops [NavigationNotificationService] on driving-mode
 * transitions (spec: navigation-ongoing-notification — active-driving
 * notification + process survival).
 *
 * Surface-independent: observes the singleton navigation state mirror and
 * the shared free-driving flag, so a driving session started only from the
 * car (deep link, no phone UI) triggers the service too. Created eagerly in
 * [com.naviveylin.NaviVeylinApp.onCreate] so the process never misses a
 * transition.
 *
 * Threading: all state observation on [Dispatchers.Main]; no work queue,
 * no wake locks — the location-typed foreground service needs neither.
 */
@Singleton
class NavigationNotificationController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateProvider: NavigationStateProvider,
    private val drivingModeProvider: DrivingModeProvider
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var serviceRunning = false

    init {
        scope.launch {
            combine(
                stateProvider.state,
                drivingModeProvider.freeDrivingActive
            ) { navState, freeDriving -> Pair(navState, freeDriving) }
                .collect { (navState, freeDriving) ->
                    val shouldRun = shouldRun(navState, freeDriving)
                    if (shouldRun && !serviceRunning) {
                        startService()
                    } else if (!shouldRun && serviceRunning) {
                        stopService()
                    }
                }
        }
    }

    /** Pure decision — unit-testable without Android. */
    internal fun shouldRun(navState: NavigationState, freeDriving: Boolean): Boolean =
        navState.isNavigating || freeDriving

    private fun startService() {
        serviceRunning = true
        val intent = Intent(context, NavigationNotificationService::class.java)
            .setAction(NavigationNotificationService.ACTION_START)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // Location-typed FGS can only start in the foreground; on the
            // rare case a driving transition happens while the process is
            // backgrounded without an eligible surface (e.g. a car-only
            // session edge case on projection), log and skip — driving itself
            // is unaffected, only the visible indicator is.
            Log.w(TAG, "startForegroundService failed", e)
            serviceRunning = false
        }
    }

    private fun stopService() {
        serviceRunning = false
        val intent = Intent(context, NavigationNotificationService::class.java)
            .setAction(NavigationNotificationService.ACTION_STOP)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "stopService failed", e)
        }
    }

    /** Release the collector (process teardown). */
    fun dispose() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "NavigationNotificationController"
    }
}
