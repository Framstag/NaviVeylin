package com.naviveylin.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto session that manages the screen stack.
 * Shows [RootScreen] when idle, [NavigationScreen] during active navigation.
 * Displays error messages from [NavigationState.errorMessage] as temporary overlays.
 *
 * Handles phone → car deep links ([DeepLinkParser]) in [onCreateScreen] and
 * [onNewIntent], starting navigation to the parsed destination.
 *
 * Startup hardening:
 * - Heavy initialization (Hilt entry point + native [OSMScoutClient] singleton)
 *   runs in the background ([startWarmup]) so the host receives its first
 *   template immediately ([LoadingScreen]) instead of blocking the main thread.
 * - Host callbacks are guarded: failures surface as [ErrorScreen] with Retry
 *   rather than killing the process.
 */
class NavigationSession : Session() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var errorJob: Job? = null
    private var warmupJob: Job? = null
    private var navigationScreen: NavigationScreen? = null

    @Volatile
    private var sessionDestroyed = false

    @Volatile
    private var warmupCompleted = false

    private var pendingIntent: Intent? = null
    private var loadingScreenShown = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                SessionLog.destroyed()
                sessionDestroyed = true
                stopObserving()
                scope.cancel()
            }
        })
        startWarmup()
    }

    private val navigationViewModel: NavigationViewModel by lazy {
        entryPoint.navigationViewModel()
    }

    private val entryPoint: AutoEntryPoint by lazy {
        val application = carContext.applicationContext
        EntryPointAccessors.fromApplication(
            application,
            AutoEntryPoint::class.java
        )
    }

    override fun onCreateScreen(intent: Intent): Screen {
        SessionLog.onCreateScreen(intent)
        return runCatching {
            if (!warmupCompleted) {
                // Host is waiting for the first template: return a lightweight
                // loading screen now; warmup pushes the real screen when ready.
                pendingIntent = intent
                loadingScreenShown = true
                LoadingScreen(carContext)
            } else {
                val screen = initialScreen()
                startObserving()
                handleDeepLink(intent)
                screen
            }
        }.getOrElse { e ->
            SessionLog.failed("onCreateScreen", e)
            ErrorScreen(
                carContext,
                "Startup failed: ${e.message ?: "unknown error"}",
                onRetry = { retryStartup() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        SessionLog.onNewIntent(intent)
        runCatching {
            if (warmupCompleted) {
                handleDeepLink(intent)
            } else {
                pendingIntent = intent
            }
        }.onFailure { e ->
            SessionLog.failed("onNewIntent", e)
        }
    }

    /**
     * Background warmup: resolve the Hilt entry point and touch the native
     * client singleton off the main thread, then hand off to the real screens.
     */
    private fun startWarmup() {
        warmupJob = scope.launch {
            val start = System.currentTimeMillis()
            withContext(Dispatchers.Default) {
                SessionLog.warmupStep("Resolving Hilt entry point")
                entryPoint
                SessionLog.warmupStep("Entry point resolved")
                SessionLog.warmupStep("Building native client")
                entryPoint.autoClientProvider().client()
                SessionLog.warmupStep("Native client ready")
            }
            SessionLog.warmupDuration(System.currentTimeMillis() - start)
            if (sessionDestroyed) return@launch
            warmupCompleted = true
            SessionLog.warmupComplete()

            val intent = pendingIntent
            pendingIntent = null
            if (intent != null) {
                handleDeepLink(intent)
            }

            if (loadingScreenShown) {
                loadingScreenShown = false
                carContext.getCarService(ScreenManager::class.java).popToRoot()
                carContext.getCarService(ScreenManager::class.java).push(initialScreen())
            }
            startObserving()
        }
    }

    /** Retry after [ErrorScreen]: reset state and re-attempt warmup. */
    private fun retryStartup() {
        if (sessionDestroyed) return
        SessionLog.retry()
        carContext.getCarService(ScreenManager::class.java).popToRoot()
        loadingScreenShown = true
        carContext.getCarService(ScreenManager::class.java).push(LoadingScreen(carContext))
        startWarmup()
    }

    private fun initialScreen(): Screen {
        return if (navigationViewModel.state.value.isNavigating) {
            getNavigationScreen()
        } else {
            RootScreen(carContext, navigationViewModel)
        }
    }

    /**
     * Parse a phone → car deep link and start navigation.
     * Coordinate destinations navigate directly; address queries are geocoded
     * via [AutoEntryPoint.autoSearchProvider] with the first match.
     */
    private fun handleDeepLink(intent: Intent) {
        val destination = DeepLinkParser.parse(intent) ?: return
        Log.d(TAG, "Deep link parsed: $destination")

        if (destination.hasCoordinates) {
            navigationViewModel.navigateTo(destination.lat!!, destination.lon!!)
            return
        }

        val query = destination.query
        if (query.isNullOrBlank()) return

        scope.launch {
            val results = withContext(Dispatchers.Default) {
                try {
                    entryPoint.autoSearchProvider().searchLocations(query, MAX_GEOCODE_RESULTS)
                } catch (e: Exception) {
                    Log.e(TAG, "Deep link geocoding failed", e)
                    emptyList()
                }
            }
            val first = results.firstOrNull()
            if (first != null) {
                Log.d(TAG, "Deep link geocoded '${query}' → ${first.label} (${first.lat}, ${first.lon})")
                navigationViewModel.navigateTo(first.lat, first.lon)
            } else {
                Log.w(TAG, "Deep link geocoding found no match for '$query'")
                navigationViewModel.reportError("No matching location found for \"$query\"")
            }
        }
    }

    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            navigationViewModel.state
                .map { it.isNavigating }
                .distinctUntilChanged()
                .collect { isNavigating ->
                    if (isNavigating) {
                        showNavigationScreen()
                    } else {
                        showRootScreen()
                    }
                }
        }
        // Observe error messages
        errorJob = scope.launch {
            navigationViewModel.state
                .map { it.errorMessage }
                .distinctUntilChanged()
                .collect { errorMsg ->
                    if (errorMsg != null) {
                        showError(errorMsg)
                    }
                }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        errorJob?.cancel()
        errorJob = null
    }

    private fun getNavigationScreen(): NavigationScreen {
        if (navigationScreen == null) {
            navigationScreen = NavigationScreen(carContext, navigationViewModel)
        }
        return navigationScreen!!
    }

    private fun showNavigationScreen() {
        Log.d(TAG, "Switching to NavigationScreen")
        SessionLog.push("NavigationScreen")
        val screen = getNavigationScreen()
        carContext.getCarService(ScreenManager::class.java).push(screen)
    }

    private fun showRootScreen() {
        Log.d(TAG, "Switching to RootScreen")
        SessionLog.popToRoot()
        navigationScreen = null
        carContext.getCarService(ScreenManager::class.java).popToRoot()
    }

    private fun showError(message: String) {
        Log.d(TAG, "Showing error: $message")
        SessionLog.errorOverlay(message)
        val errorScreen = object : Screen(carContext) {
            override fun onGetTemplate(): PaneTemplate {
                val pane = Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle(message)
                            .build()
                    )
                    .build()
                return PaneTemplate.Builder(pane)
                    .setTitle("Error")
                    .build()
            }
        }
        carContext.getCarService(ScreenManager::class.java).push(errorScreen)

        // Auto-dismiss error after 4 seconds, clear error state
        scope.launch {
            delay(ERROR_DISPLAY_MS)
            navigationViewModel.clearError()
            carContext.getCarService(ScreenManager::class.java).popToRoot()
        }
    }

    companion object {
        private const val TAG = "NavigationSession"
        private const val ERROR_DISPLAY_MS = 4000L
        private const val MAX_GEOCODE_RESULTS = 1
    }
}
