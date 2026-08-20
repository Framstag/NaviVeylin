package com.naviveylin.auto

import android.content.Intent
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
 *   runs in the background ([startWarmup]) so the host gets a responsive session;
 *   the root screen ([RootScreen]/[NavigationScreen]) is shown immediately and
 *   does not block on the native client (only the map screen uses it).
 * - Host callbacks are guarded: failures surface as [ErrorScreen] with Retry
 *   rather than killing the process.
 * - The root screen is always the real one: a transient LoadingScreen was never
 *   a viable root because androidx.car.app 1.7 [ScreenManager] refuses to pop
 *   the root screen, so any later popToRoot() would re-reveal the stale
 *   loading template and wedge the session on it forever.
 * - Warmup is time-boxed ([WARMUP_TIMEOUT_MS]): a stuck native client build
 *   must never block the session; on timeout the session surfaces
 *   [ErrorScreen] with the last completed warmup step.
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

    @Volatile
    private var lastWarmupStep: String = "warmup not started"

    /** Wall clock at Session construction — anchors all elapsed-time logs. */
    private val sessionStartMs: Long = System.currentTimeMillis()

    private var pendingIntent: Intent? = null

    /** Set once the AA location source has been started (guards [entryPoint] in onDestroy). */
    @Volatile
    private var locationStarted = false

    init {
        SessionLog.sessionCreated()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                SessionLog.destroyed()
                if (warmupJob?.isActive == true) {
                    SessionLog.warmupCancelled()
                }
                if (locationStarted) {
                    runCatching { entryPoint.autoLocationProvider().stop() }
                        .onFailure { Log.w(TAG, "location stop failed", it) }
                }
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
        SessionLog.onCreateScreen(
            intent,
            warmupCompleted = warmupCompleted,
            sinceSessionMs = System.currentTimeMillis() - sessionStartMs
        )
        return runCatching {
            // The root screen is always the real one (RootScreen or
            // NavigationScreen). It does not need the native client, so it can
            // be served as the very first template without waiting for warmup.
            // Never return a transient LoadingScreen here: it would become the
            // stack root and, because ScreenManager cannot pop the root, any
            // later popToRoot() would re-reveal it and wedge the session.
            val screen = initialScreen()
            if (warmupCompleted) {
                startObserving()
                handleDeepLink(intent)
            } else {
                // Warmup still running; the deep link is processed when it
                // finishes (see onWarmupComplete).
                pendingIntent = intent
            }
            screen
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
        SessionLog.onNewIntent(intent, System.currentTimeMillis() - sessionStartMs)
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
     *
     * Never lets the session wedge:
     * - exceptions are caught and surfaced as [ErrorScreen] instead of killing
     *   the process (a warmup crash previously left the host in an error/restart
     *   loop with no usable diagnostic);
     * - the whole init is time-boxed by [WARMUP_TIMEOUT_MS]. A native client
     *   build that blocks forever can not be interrupted cooperatively, so the
     *   timeout merely stops waiting and reports the last completed step — the
     *   stuck thread leaks but the session stays usable and Retryable after the
     *   host restarts the process.
     */
    private fun startWarmup() {
        warmupJob = scope.launch {
            SessionLog.warmupStarted()
            val result = runCatching {
                withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
                    val start = System.currentTimeMillis()
                    var lastStepAt = start
                    withContext(Dispatchers.Default) {
                        lastWarmupStep = "Resolving Hilt entry point"
                        SessionLog.warmupStep(lastWarmupStep, 0)
                        entryPoint
                        lastStepAt = step("Entry point resolved", lastStepAt)
                        lastWarmupStep = "Building native client"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)
                        lastStepAt = System.currentTimeMillis()
                        entryPoint.autoClientProvider().client()
                        lastWarmupStep = "Native client ready"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)

                        // Activate the AA navigation controller: its init wires
                        // itself into the shared state provider, so "Navigate
                        // here" and turn-by-turn work without the phone UI.
                        lastStepAt = System.currentTimeMillis()
                        lastWarmupStep = "Activating navigation controller"
                        SessionLog.warmupStep(lastWarmupStep, 0)
                        try {
                            entryPoint.autoNavigationController()
                        } catch (e: Exception) {
                            Log.w(TAG, "autoNavigationController init failed", e)
                        }
                        lastWarmupStep = "Navigation controller ready"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)

                        // Mirror the phone app's initMap(): open every installed
                        // map database so the map renders and search/geocoding
                        // find results, and init the favorites repository.
                        // Best effort — failures are logged, not fatal.
                        lastStepAt = System.currentTimeMillis()
                        lastWarmupStep = "Opening installed map databases"
                        SessionLog.warmupStep(lastWarmupStep, 0)
                        try {
                            val filesDir = carContext.applicationContext.filesDir.absolutePath
                            entryPoint.autoClientProvider().openMapDatabases(filesDir)
                        } catch (e: Exception) {
                            Log.w(TAG, "openMapDatabases failed", e)
                        }
                        lastWarmupStep = "Opening map databases done"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)

                        lastStepAt = System.currentTimeMillis()
                        lastWarmupStep = "Initializing favorites"
                        SessionLog.warmupStep(lastWarmupStep, 0)
                        try {
                            val favoritesFile = File(
                                carContext.applicationContext.filesDir,
                                FAVORITES_FILE
                            ).absolutePath
                            entryPoint.autoFavoritesProvider().init(favoritesFile)
                        } catch (e: Exception) {
                            Log.w(TAG, "favorites init failed", e)
                        }
                        lastWarmupStep = "Favorites ready"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)
                    }
                    val total = System.currentTimeMillis() - start
                    SessionLog.warmupBlockDone(total)
                    SessionLog.warmupDuration(total)
                    true
                }
            }
            if (sessionDestroyed) return@launch

            val exception = result.exceptionOrNull()
            if (exception != null) {
                if (exception is CancellationException) return@launch
                SessionLog.failed("startWarmup", exception)
                showStartupFailure(
                    "Startup failed: ${exception.message ?: exception.javaClass.simpleName}"
                )
            } else {
                if (result.getOrNull() == true) {
                    warmupCompleted = true
                    SessionLog.warmupComplete()
                    onWarmupComplete()
                } else {
                    DiagnosticsLog.log(
                        SessionLog.SESSION_TAG,
                        "Warmup timed out after ${WARMUP_TIMEOUT_MS}ms (last step: $lastWarmupStep)"
                    )
                    showStartupFailure(
                        "Map data initialization timed out after ${WARMUP_TIMEOUT_MS / 1000}s " +
                            "(last step: $lastWarmupStep). Retry or restart the app."
                    )
                }
            }
        }
    }

    /** Log a warmup step with elapsed time since [sinceMs]; returns the new timestamp. */
    private fun step(step: String, sinceMs: Long): Long {
        val now = System.currentTimeMillis()
        lastWarmupStep = step
        SessionLog.warmupStep(step, now - sinceMs)
        return now
    }

    /** Runs on the main thread once warmup succeeded. Never throws out. */
    private fun onWarmupComplete() {
        runCatching {
            val intent = pendingIntent
            pendingIntent = null
            if (intent != null) {
                handleDeepLink(intent)
            }
            startLocation()
            startObserving()
        }.onFailure { e ->
            SessionLog.failed("onWarmupComplete", e)
            showStartupFailure("Startup failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Start the AA location source (GPS marker + follow mode on the car map). */
    private fun startLocation() {
        runCatching {
            entryPoint.autoLocationProvider().start()
            locationStarted = true
        }.onFailure { e ->
            Log.w(TAG, "location start failed", e)
        }
    }

    /** Surface an actionable error (with Retry) on top of the root screen. */
    private fun showStartupFailure(message: String) {
        if (sessionDestroyed) return
        DiagnosticsLog.log(SessionLog.SESSION_TAG, "Showing ErrorScreen: $message")
        runCatching {
            carContext.getCarService(ScreenManager::class.java)
                .push(ErrorScreen(carContext, message, onRetry = { retryStartup() }))
        }.onFailure { e ->
            SessionLog.failed("showStartupFailure", e)
        }
    }

    /** Retry after [ErrorScreen]: reset state and re-attempt warmup. */
    private fun retryStartup() {
        if (sessionDestroyed) return
        SessionLog.retry()
        carContext.getCarService(ScreenManager::class.java).popToRoot()
        startWarmup()
    }

    private fun initialScreen(): Screen {
        return if (navigationViewModel.state.value.isNavigating) {
            getNavigationScreen()
        } else {
            // Open in map view by default; the menu is reachable via the map's
            // "Menu" action (RootScreen is no longer the stack root).
            MapScreen(carContext, navigationViewModel)
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
            init {
                enableBackNavigation()
            }

            override fun onGetTemplate(): PaneTemplate {
                val backAction = Action.Builder()
                    .setTitle("Back")
                    .setOnClickListener { screenManager.pop() }
                    .build()
                val pane = Pane.Builder()
                    .addRow(
                        Row.Builder()
                            .setTitle(message)
                            .addAction(backAction)
                            .build()
                    )
                    .build()
                return PaneTemplate.Builder(pane)
                    .setHeader(Header.Builder().setTitle("Error").setStartHeaderAction(Action.BACK).build())
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

        /** Same favorites persistence file as the phone app (MapCanvasViewModel). */
        private const val FAVORITES_FILE = "favorites.json"

        /**
         * Cap on cold-start warmup (Hilt entry point + native client build).
         * A healthy first init usually takes a few seconds; give slow devices
         * headroom, but never let the LoadingScreen stay up indefinitely.
         */
        private const val WARMUP_TIMEOUT_MS = 45_000L
    }
}
