package com.naviveylin.auto

import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.navigation.NavigationManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.car.app.navigation.model.Trip
import com.naviveylin.auto.R
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.DeepLinkDestination
import com.naviveylin.core.DeepLinkParser
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.NavigationState
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.RouteCalculation
import com.naviveylin.core.StringResolver
import com.naviveylin.core.SurfaceOrigin
import com.naviveylin.core.stringResolver
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android Auto session that manages the screen stack.
 * The stack root is always the browse-map [MapScreen] (design D1 — the root
 * cannot be popped, so a transient-mode view must never be rooted);
 * [NavigationScreen] is pushed while navigation is active, free driving on
 * demand. Displays error messages from [NavigationState.errorMessage] as
 * temporary overlays.
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

    /**
     * The session's own scope, with the car-facing fault handler (spec:
     * car-host-fault-isolation — No fault escapes into the host path; design D3/D7): the
     * observers, the trip publisher and the screen-stack mutations run here, and an
     * exception from any of them would otherwise reach the main thread's uncaught handler
     * and kill the process.
     */
    private val scope = carSessionScope()
    private var observeJob: Job? = null
    private var errorJob: Job? = null
    private var tripJob: Job? = null
    private var noticeJob: Job? = null
    private var warmupJob: Job? = null
    private var errorDismissalJob: Job? = null
    private var navigationScreen: NavigationScreen? = null

    /**
     * Screen-stack bookkeeping (spec: car-host-fault-isolation — Host screen-stack
     * mutations are balanced; design D4). [showNavigationScreen] skips the push while the
     * navigation view is up — the screen re-renders from its own state collector, so a
     * navigation restart needs no duplicate screen. The record follows the mutation that
     * succeeded, never the attempt: recording a refused push would make this early-return
     * forever. [ScreenManager] exposes no public top-screen getter, hence the flag.
     */
    private val screenStack = SessionScreenStack()

    /**
     * The error notice currently on the stack, or null. Held so the dismissal removes
     * exactly this screen (`ScreenManager.remove`) instead of popping the stack back to
     * the root — which used to take the navigation view down with it (design D5).
     */
    private var errorNotice: ErrorOverlayScreen? = null

    /**
     * The route-calculation wait notice on the stack, or null (spec:
     * `route-calculation-feedback` — Car wait notice while a route is being calculated).
     * Held so the removal names exactly this screen and a stale removal cannot take a
     * newer notice down (design D5).
     */
    private var calculationNotice: RouteCalculatingScreen? = null

    /**
     * The percentage step the notice currently displays, or null while no notice is up. Kept so a
     * percent change *inside* the same 5 % step costs no host push (design D5).
     */
    private var calculationNoticeBucket: Int? = null

    /**
     * The delay that keeps a fast route from flashing a screen, and the calculation token it
     * was armed for: a calculation that ends (or is superseded) before the delay elapses
     * never raises a notice.
     */
    private var noticeArmJob: Job? = null
    private var noticeArmToken: Long? = null

    @Volatile
    private var sessionDestroyed = false

    @Volatile
    private var warmupCompleted = false

    /**
     * Host-mutation gate (spec: car-host-fault-isolation — Bounded host-facing traffic
     * while not visible): the session's observers keep running while the app is
     * backgrounded (guidance and trip metadata must keep updating), but the host state
     * is only mutated while the session is started.
     */
    private val hostGate = SessionHostGate()

    /**
     * At-most-once bookkeeping for the free-driving restore (spec: auto/free-driving —
     * Free-driving restore is idempotent): [restoreDrivingMode] runs from two session
     * paths and the free-driving flag survives a session destroy, so the push must be
     * consumed once per session.
     */
    private val freeDrivingRestore = FreeDrivingRestoreGate()

    @Volatile
    private var lastWarmupStep: String = "warmup not started"

    /** Wall clock at Session construction — anchors all elapsed-time logs. */
    private val sessionStartMs: Long = System.currentTimeMillis()

    private var pendingIntent: Intent? = null

    /**
     * Host day/night state (head unit decides; phone system mode is NOT the
     * source). Lazy: [CarContext] is only available after the framework calls
     * [Session.configure] — accessing it in a property initializer crashes.
     * Initialized from [CarContext.isDarkMode] (false until the host sends
     * configuration) and corrected by [onCarConfigurationChanged]. Screens
     * collect this to push the stylesheet `daylight` flag.
     */
    private val _hostDark: MutableStateFlow<Boolean> by lazy {
        MutableStateFlow(carContext.isDarkMode())
    }
    val hostDark: StateFlow<Boolean> by lazy { _hostDark.asStateFlow() }

    /**
     * Shared dark mode preference ("ON"/"OFF"/"AUTOMATIC") driving the car
     * rendering, seeded from persisted settings at session start (default
     * AUTOMATIC until then / on load failure). Updated by
     * [PreferencesScreen] via [updateDarkModePreference].
     */
    private val _darkModePref: MutableStateFlow<String> by lazy {
        MutableStateFlow("AUTOMATIC")
    }

    /**
     * Resolved dark presentation for the car map screens: **ON** forces dark,
     * **OFF** forces light, **AUTOMATIC** follows the host day/night signal
     * (see [hostDark]). Screens collect this instead of [hostDark] so the
     * shared dark mode preference takes effect on the car (spec:
     * auto/preferences — dark mode preference applies to car rendering).
     */
    val resolvedDark: StateFlow<Boolean> by lazy {
        combine(_hostDark, _darkModePref) { hostDark, pref ->
            resolveCarDark(pref, hostDark)
        }.stateIn(scope, SharingStarted.Eagerly, carContext.isDarkMode())
    }

    /** Feed the current dark mode preference into [resolvedDark]. */
    fun updateDarkModePreference(pref: String) {
        if (_darkModePref.value != pref) {
            _darkModePref.value = pref
        }
    }

    override fun onCarConfigurationChanged(configuration: Configuration) {
        super.onCarConfigurationChanged(configuration)
        val dark = isNightUiMode(configuration)
        if (_hostDark.value != dark) {
            SessionLog.hostDarkChanged(dark)
            _hostDark.value = dark
        }
    }

    /** Set once the AA location source has been started (guards [entryPoint] in onDestroy). */
    @Volatile
    private var locationStarted = false

    init {
        SessionLog.sessionCreated()
        // Seed the shared dark mode preference for the car rendering; best
        // effort — AUTOMATIC stays the value until the load completes.
        scope.launch(CoroutineName("dark-mode-preference")) {
            runCatching { entryPoint.autoSettingsProvider().load().darkMode }
                .onSuccess { updateDarkModePreference(it) }
                .onFailure { Log.w(TAG, "dark mode preference load failed — default AUTOMATIC", it) }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Each lifecycle step is confined on its own (spec: car-host-fault-isolation —
                // No fault escapes into the host path, "Session lifecycle callback throws"):
                // the car-app library dispatches these on the app's main thread and rethrows
                // an escaping exception there, and a failed registration must not skip the
                // host sync that follows it.
                //
                // The car-app host keeps ONE surface callback per app: registering it
                // per session (not per screen) is what stops a screen transition from
                // re-delivering the surface, and it is why no screen releases a
                // surface another screen draws through (spec: car-host-fault-isolation
                // — Single-owner car surface; design D1).
                guardedHostCall("register car surface callback", tag = SESSION_DIAG_TAG) {
                    surfaceHost().startSession(carContext)
                }
                // The phone surface is told that a car session is live (spec:
                // `car-session-presence`) — informational only, confined like every
                // other lifecycle step (the library rethrows on the main thread).
                guardedHostCall("publish car session presence", tag = SESSION_DIAG_TAG) {
                    carSessionPresence().setActive(true)
                }
                SessionLog.push("car session presence: live")
                SessionLog.push("surface callback registered")
                // A transition that happened while the session was stopped is applied
                // once now (spec: car-host-fault-isolation — Bounded host-facing traffic
                // while not visible).
                var syncOwed = false
                guardedHostCall("host gate onSessionStart", tag = SESSION_DIAG_TAG) {
                    syncOwed = hostGate.onSessionStart()
                }
                if (syncOwed) {
                    SessionLog.push("host sync after background")
                    syncHostWithCurrentState()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                // Host mutations (screen push/pop, template refresh, host navigation
                // state) are deferred from here on. Confined like every lifecycle step:
                // this runs inside the library's dispatch, which rethrows on the main thread.
                guardedHostCall("host gate onSessionStop", tag = SESSION_DIAG_TAG) {
                    hostGate.onSessionStop()
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                SessionLog.destroyed()
                if (warmupJob?.isActive == true) {
                    SessionLog.warmupCancelled()
                }
                // Every cleanup step is confined separately (spec: car-host-fault-isolation —
                // "Session lifecycle callback throws"): one failing step must not skip the
                // steps after it, and none of them may escape into the library's dispatch.
                if (locationStarted) {
                    guardedHostCall("stop location source", tag = SESSION_DIAG_TAG) {
                        entryPoint.autoLocationProvider().stop()
                    }
                }
                // Clear the presence signal: the session is gone, and a stale "live"
                // value must not outlive it (spec: `car-session-presence`).
                guardedHostCall("clear car session presence", tag = SESSION_DIAG_TAG) {
                    carSessionPresence().setActive(false)
                }
                SessionLog.push("car session presence: ended")
                // NavigationManager cleanup (may be mid-navigation; the
                // controller never throws). Only if it was ever created.
                guardedHostCall("navigation manager destroy", tag = SESSION_DIAG_TAG) {
                    navigationManagerController?.onDestroy()
                }
                // The driver left the car with the destination already reached: the navigation
                // this session presented ends with the session (spec: `auto/navigation-view` —
                // Navigation ends when the car session ends after arrival). Ordered after the host
                // navigation teardown above, so the still-live trip collector cannot publish a
                // trip built from the state this resets; confined like every other teardown step,
                // because a fault here must not escape into the library's dispatch. Reading the
                // engine's state is the whole guard: a session that never navigated reads the
                // default, and the predicate turns that into a no-op.
                guardedHostCall("end navigation after arrival", tag = SESSION_DIAG_TAG) {
                    val endState = navigationViewModel.state.value
                    val ended = endNavigationAfterArrival(endState, navigationViewModel::stopNavigation)
                    SessionLog.navigationEndDecision(endState, ended)
                }
                sessionDestroyed = true
                guardedHostCall("stop observing", tag = SESSION_DIAG_TAG) { stopObserving() }
                // Releases a still-held surface and clears the host registration
                // (spec: car-host-fault-isolation — "Session ends with a surface held").
                guardedHostCall("end car session surface", tag = SESSION_DIAG_TAG) {
                    surfaceHost().endSession()
                }
                guardedHostCall("cancel session scope", tag = SESSION_DIAG_TAG) { scope.cancel() }
            }
        })
        startWarmup()
    }

    private val navigationViewModel: NavigationViewModel by lazy {
        entryPoint.navigationViewModel()
    }

    /**
     * Process-wide car-session-presence publisher (spec: `car-session-presence`):
     * resolved lazily so a session that never starts (or fails during warmup) does
     * not construct the phone-facing seam.
     */
    private fun carSessionPresence() = entryPoint.carSessionPresence()

    /**
     * NavigationManager lifecycle (spec: auto/navigation-view — "Leave
     * navigation at any time"): registers the callback + navigationStarted()
     * on navigation start, navigationEnded() + clear on stop, so the host ETA
     * card stop button works. Null until first navigation start; [CarContext]
     * is only available after [Session.configure].
     */
    private var navigationManagerController: NavigationManagerController? = null

    private fun navigationManagerController(): NavigationManagerController =
        navigationManagerController ?: NavigationManagerController(
            carContext.getCarService(NavigationManager::class.java),
            onStop = { navigationViewModel.stopNavigation() }
        ).also { navigationManagerController = it }

    private val entryPoint: AutoEntryPoint by lazy {
        val application = carContext.applicationContext
        EntryPointAccessors.fromApplication(
            application,
            AutoEntryPoint::class.java
        )
    }

    /** Session-scoped car surface owner (spec: car-host-fault-isolation). */
    private fun surfaceHost(): com.naviveylin.core.CarSurfaceHost = entryPoint.autoSurfaceHost()

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
            // Restore a still-active car mode (free driving) as a PUSHED screen
            // once the observer is live — never as the session root (D1). The
            // navigation case is covered by the observer's first emission.
            restoreDrivingMode()
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
        warmupJob = scope.launch(CoroutineName("warmup")) {
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

                        // Init the favorites repository as soon as the client is
                        // built: favorites depend only on the client (JNI + JSON
                        // file), not on map databases, so they become available
                        // before the (slower) map database open. Best effort —
                        // failures are logged, not fatal.
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

                        // Resolve the process-scoped navigation engine: a car-only
                        // process must instantiate it, so "Navigate here" and
                        // turn-by-turn work without the phone UI (spec:
                        // `auto-cross-device-sync` — Car-only navigation start).
                        lastStepAt = System.currentTimeMillis()
                        lastWarmupStep = "Activating navigation engine"
                        SessionLog.warmupStep(lastWarmupStep, 0)
                        try {
                            entryPoint.navigationViewModel()
                        } catch (e: Exception) {
                            Log.w(TAG, "navigation engine init failed", e)
                        }
                        lastWarmupStep = "Navigation engine ready"
                        SessionLog.warmupStep(lastWarmupStep, System.currentTimeMillis() - lastStepAt)

                        // Mirror the phone app's initMap(): open every installed
                        // map database so the map renders and search/geocoding
                        // find results. Best effort — failures are logged, not
                        // fatal.
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
            restoreDrivingMode()
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
        if (!hostGate.allowHostMutation()) {
            // Backgrounded: no visible screen to show it on. The failure is recorded in
            // the diagnostics log; the retry affordance is not needed off-screen.
            DiagnosticsLog.log(SessionLog.SESSION_TAG, "Startup failure while backgrounded: $message")
            return
        }
        DiagnosticsLog.log(SessionLog.SESSION_TAG, "Showing ErrorScreen: $message")
        guardedHostCall("push ErrorScreen (startup failure)") {
            carContext.getCarService(ScreenManager::class.java)
                .push(ErrorScreen(carContext, message, onRetry = { retryStartup() }))
        }
    }

    /** Retry after [ErrorScreen]: reset state and re-attempt warmup. */
    private fun retryStartup() {
        if (sessionDestroyed) return
        SessionLog.retry()
        val landed = guardedHostCall("popToRoot (startup retry)") {
            carContext.getCarService(ScreenManager::class.java).popToRoot()
        }
        if (landed) {
            screenStack.onRootPopped()
            navigationScreen = null
            // The stack is back at the root: a still-active free-driving mode may be
            // restored again (spec: auto/free-driving — Free-driving restore is idempotent).
            // Only when the pop landed — otherwise the previous free-driving view is still
            // on the stack and a second push would stack two of them.
            freeDrivingRestore.reset()
        }
        startWarmup()
    }

    private fun initialScreen(): Screen {
        // The session root is ALWAYS the browse map (design D1, spec:
        // auto/navigation-view — "Leave navigation at any time"): the car-app
        // ScreenManager cannot pop the root, so a transient-mode view
        // (navigation or free driving) restored at session start must never be
        // rooted — when its mode ends, the stack could never leave it (bare
        // map + dead back "X"). Restored modes are PUSHED on top instead:
        // navigation via the observer's first state emission, free driving via
        // [restoreDrivingMode].
        return MapScreen(
            carContext,
            navigationViewModel,
            resolvedDark = resolvedDark,
            onDarkModeChanged = ::updateDarkModePreference
        )
    }

    /**
     * Parse a phone → car deep link and start navigation.
     * Coordinate destinations navigate directly; address queries are geocoded
     * via [AutoEntryPoint.autoSearchProvider] with the first match.
     */
    private fun handleDeepLink(intent: Intent) {
        val destination = DeepLinkParser.parse(intent) ?: return
        Log.d(TAG, deepLinkLogMessage(destination))

        if (destination.hasCoordinates) {
            navigationViewModel.navigateTo(destination.lat!!, destination.lon!!)
            return
        }

        val query = destination.query
        if (query.isNullOrBlank()) return

        scope.launch(CoroutineName("deep-link-geocode")) {
            val results = withContext(Dispatchers.Default) {
                try {
                    // No position context for a deep link: a null reference orders
                    // the candidates by match tier and quality, which is what a
                    // "best match" pick wants (spec: search-result-ranking).
                    entryPoint.autoSearchProvider().searchLocations(query, MAX_GEOCODE_RESULTS, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Deep link geocoding failed", e)
                    emptyList()
                }
            }
            val first = results.firstOrNull()
            if (first != null) {
                Log.d(TAG, "Deep link geocoded '${query}' → ${first.label}")
                navigationViewModel.navigateTo(first.lat, first.lon)
            } else {
                Log.w(TAG, "Deep link geocoding found no match for '$query'")
                navigationViewModel.reportError(
                    "No matching location found for \"$query\"",
                    SurfaceOrigin.CAR
                )
            }
        }
    }

    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch(CoroutineName("navigation-state")) {
            navigationViewModel.state
                .map { it.isNavigating }
                .distinctUntilChanged()
                .collect { isNavigating ->
                    // Host navigation state and the screen stack are both host
                    // mutations: while the session is not started they are deferred and
                    // re-applied once on the next start (spec: car-host-fault-isolation —
                    // Bounded host-facing traffic while not visible).
                    if (!hostGate.allowHostMutation()) {
                        Log.d(TAG, "session not started — navigation view sync deferred")
                        return@collect
                    }
                    if (isNavigating) {
                        navigationManagerController().onNavigationStarted()
                        showNavigationScreen()
                    } else {
                        navigationManagerController()?.onNavigationEnded()
                        showRootScreen()
                    }
                }
        }
        // Trip metadata for the cluster / heads-up display (spec:
        // auto-navigation-hints — "Trip metadata for cluster and heads-up
        // display"). Separate collector: this one needs every state emission,
        // not just the isNavigating edges. Launched after the navigation-state
        // collector, which is what declares the app as the active navigation
        // app before any updateTrip is sent. The first emission also seeds a
        // session that was created (or restored) mid-navigation.
        tripJob = scope.launch(CoroutineName("trip")) {
            navigationViewModel.state.collect { navState ->
                navigationManagerController().publishTrip(navState) { state ->
                    carTripFor(state, carContext.stringResolver())
                }
            }
        }
        // Observe error messages. Only errors that concern the car are shown: the
        // engine's own errors (ENGINE) and errors raised for the car — never one the
        // phone surface caused (spec: `navigation-engine` — Errors carry the surface
        // that caused them).
        errorJob = scope.launch(CoroutineName("error")) {
            navigationViewModel.state
                .map { navState ->
                    navState.errorMessage.takeIf { navState.errorAppliesTo(SurfaceOrigin.CAR) }
                }
                .distinctUntilChanged()
                .collect { errorMsg ->
                    if (errorMsg != null) {
                        showError(errorMsg)
                    }
                }
        }
        // Observe the route calculation in flight and raise the wait notice once it has
        // outlasted the delay (spec: `route-calculation-feedback` — Car wait notice while a
        // route is being calculated). The engine publishes the calculation immediately; the
        // delay lives here, at the display site, so the phone is not made to wait for it
        // (design D5). A percent change re-runs the sync, which updates the notice in place.
        noticeJob = scope.launch(CoroutineName("calculation-notice")) {
            navigationViewModel.state
                .map { navState -> navState.calculation }
                .distinctUntilChanged()
                .collect { calculation -> onCalculationChanged(calculation) }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        errorJob?.cancel()
        errorJob = null
        tripJob?.cancel()
        tripJob = null
        noticeJob?.cancel()
        noticeJob = null
        noticeArmJob?.cancel()
        noticeArmJob = null
        noticeArmToken = null
    }

    private fun getNavigationScreen(): NavigationScreen {
        if (navigationScreen == null) {
            navigationScreen = NavigationScreen(carContext, navigationViewModel, resolvedDark = resolvedDark)
        }
        return navigationScreen!!
    }

    private fun showNavigationScreen() {
        Log.d(TAG, "Switching to NavigationScreen")
        if (screenStack.isNavigationShown) {
            // The navigation screen is already on the stack; it re-renders in
            // place from its own state collector (D4). Never push a duplicate.
            Log.d(TAG, "NavigationScreen already on stack — no re-push")
            return
        }
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — navigation screen push deferred")
            return
        }
        SessionLog.push("NavigationScreen")
        val screen = getNavigationScreen()
        val landed = guardedHostCall("push NavigationScreen") {
            carContext.getCarService(ScreenManager::class.java).push(screen)
        }
        // Only a landed push is recorded: claiming a screen the host refused makes this
        // method early-return forever and the next state emission never re-pushes
        // (spec: car-host-fault-isolation — Host screen-stack mutations are balanced).
        screenStack.onNavigationPush(landed)
    }

    private fun showRootScreen() {
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — root screen pop deferred")
            return
        }
        Log.d(TAG, "Switching to RootScreen")
        SessionLog.popToRoot()
        val landed = guardedHostCall("popToRoot (root screen)") {
            carContext.getCarService(ScreenManager::class.java).popToRoot()
        }
        if (landed) {
            navigationScreen = null
            screenStack.onRootPopped()
        }
        // A rejected pop keeps the cached screen and the record: the screen is still on
        // the stack, so forgetting it would make the next push a duplicate. The retry path
        // is the deferred host sync (a pop attempted while the session is stopped is
        // re-applied on the next start).
    }

    /**
     * Apply the state the session finds on its first started emission after a deferred
     * (backgrounded) transition (spec: car-host-fault-isolation — Bounded host-facing
     * traffic while not visible): the navigation screen and the host navigation state
     * are brought in line exactly once, so a transition that happened while stopped is
     * not lost.
     */
    private fun syncHostWithCurrentState() {
        // Each application step is confined on its own, so a fault in one of them still
        // lets the other run (spec: car-host-fault-isolation — No fault escapes into the
        // host path).
        guardedHostCall("sync host navigation state", tag = SESSION_DIAG_TAG) {
            if (navigationViewModel.state.value.isNavigating) {
                navigationManagerController().onNavigationStarted()
                showNavigationScreen()
            } else {
                navigationManagerController()?.onNavigationEnded()
                showRootScreen()
            }
        }
        // An error raised while the session was stopped is shown once now — car
        // errors and engine-wide ones only.
        guardedHostCall("show deferred error notice", tag = SESSION_DIAG_TAG) {
            val navState = navigationViewModel.state.value
            navState.errorMessage
                ?.takeIf { navState.errorAppliesTo(SurfaceOrigin.CAR) }
                ?.let { showError(it) }
        }
        // A notice whose dismissal was skipped while the session was stopped is removed
        // once now that host mutations are allowed again (spec: car-host-fault-isolation —
        // "Deferred mutation after the session ended").
        val owedNotice = screenStack.consumeOwedDismissal() as? ErrorOverlayScreen
        if (owedNotice != null) {
            val removed = guardedHostCall("remove ErrorOverlayScreen (deferred)") {
                carContext.getCarService(ScreenManager::class.java).remove(owedNotice)
            }
            if (removed) {
                if (errorNotice === owedNotice) errorNotice = null
                screenStack.onErrorNoticeDismissed(owedNotice)
            }
        }
        // The wait notice belongs to the calculation in flight: re-applied once now (a push
        // deferred by the stop), and — the other way round — a notice whose calculation ended
        // while the session was stopped is removed here and never shown
        // (spec: `route-calculation-feedback` — The car notice never outlives its calculation).
        guardedHostCall("sync calculation notice", tag = SESSION_DIAG_TAG) {
            applyCalculationNotice(navigationViewModel.state.value.calculation)
        }
        val owedCalculationNotice =
            screenStack.consumeOwedCalculationDismissal() as? RouteCalculatingScreen
        if (owedCalculationNotice != null) {
            val removed = guardedHostCall("remove RouteCalculatingScreen (deferred)") {
                carContext.getCarService(ScreenManager::class.java).remove(owedCalculationNotice)
            }
            if (removed) {
                if (calculationNotice === owedCalculationNotice) calculationNotice = null
                screenStack.onCalculationNoticeDismissed(owedCalculationNotice)
            }
        }
    }

    /**
     * Restore a still-active free-driving session as a PUSHED screen (D1).
     * Called after the observer is live so the stack root (always the browse
     * map) is already in place; navigation restore needs no such helper — the
     * observer's first emission pushes the navigation screen. Gated on
     * `!isNavigating`: the free-driving and navigation modes are mutually
     * exclusive (the navigation controller clears the free-driving flag on
     * navigation start), and the gate keeps a race from stacking both views.
     *
     * At most once per session (spec: auto/free-driving — Free-driving restore is
     * idempotent): [onCreateScreen] and [onWarmupComplete] both call this, and the
     * free-driving flag survives a session destroy, so an unguarded restore pushed the
     * view twice (two renderers, plus a ghost view under the top one).
     */
    private fun restoreDrivingMode() {
        if (sessionDestroyed) return
        val shouldRestore = freeDrivingRestore.shouldPush(
            isNavigating = navigationViewModel.state.value.isNavigating,
            freeDrivingActive = entryPoint.autoDrivingModeProvider().freeDrivingActive.value
        )
        if (!shouldRestore) return
        if (!hostGate.allowHostMutation()) {
            // Deferred, not consumed: the next started sync restores it.
            Log.d(TAG, "session not started — free-driving restore deferred")
            return
        }
        freeDrivingRestore.recordPush(landed = false)
        SessionLog.push("FreeDrivingScreen (restore)")
        val landed = guardedHostCall("push FreeDrivingScreen (restore)") {
            carContext.getCarService(ScreenManager::class.java).push(FreeDrivingScreen(carContext, resolvedDark))
        }
        // Only a landed push consumes the session's one restore (spec:
        // car-host-fault-isolation — Host screen-stack mutations are balanced): consuming it
        // on a refused push loses the free-driving view for the rest of the session.
        freeDrivingRestore.recordPush(landed)
    }
    /**
     * A change of the route calculation in flight: show, update or remove the wait notice.
     *
     * The notice is a host mutation, so while the session is not started the gate defers the
     * sync (and owes it to the next start). A calculation that ended in the meantime is then
     * never shown — there is no notice for it to outlive
     * (spec: `route-calculation-feedback` — The car notice never outlives its calculation).
     */
    private fun onCalculationChanged(calculation: RouteCalculation?) {
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — calculation notice sync deferred")
            return
        }
        applyCalculationNotice(calculation)
    }

    /**
     * Show, update or remove the notice for [calculation]. Host mutations are allowed here —
     * the caller checked the gate (the started sync and the observer both go through
     * [onCalculationChanged], the sync through a guarded call).
     */
    private fun applyCalculationNotice(calculation: RouteCalculation?) {
        if (sessionDestroyed) return
        if (calculation == null) {
            cancelNoticeArm()
            if (noticeIsStale(calculation, noticeShown = calculationNotice != null)) {
                removeCalculationNotice()
            }
            return
        }
        val notice = calculationNotice
        if (notice != null) {
            // A notice is up: update it instead of stacking a second screen (spec:
            // `route-calculation-feedback` — One notice only) — and only when the *displayed*
            // step changed, so a progressing calculation costs one push per 5 %, not one per
            // percent (design D5).
            val shown = calculation.percent
            if (!noticeNeedsUpdate(calculationNoticeBucket, shown)) return
            calculationNoticeBucket = displayedCalculationPercent(shown)
            notice.update(navigationViewModel.state.value.destinationName, shown)
            DiagnosticsLog.log(
                CAR_HOST_DIAG_TAG,
                "calculation notice update pct=$calculationNoticeBucket"
            )
            guardedHostCall("invalidate calculation notice") { notice.invalidate() }
            return
        }
        if (!needsNoticeArm(
                live = calculation,
                noticeShown = false,
                armedToken = noticeArmToken,
                arming = noticeArmJob?.isActive == true
            )
        ) {
            return
        }
        cancelNoticeArm()
        val token = calculation.token
        noticeArmToken = token
        noticeArmJob = scope.launch(CoroutineName("calculation-notice-delay")) {
            delay(CALCULATION_NOTICE_DELAY_MS)
            // Still the live calculation? The engine's state is the truth and the delay is only
            // ours, so a calculation that ended — or a newer one that superseded it — must not
            // raise this notice (spec: `route-calculation-feedback`).
            if (!noticeIsStillArmedFor(token, navigationViewModel.state.value.calculation)) {
                Log.d(TAG, "calculation notice not shown: the wait is over")
                return@launch
            }
            pushCalculationNotice(calculation)
        }
    }

    private fun cancelNoticeArm() {
        noticeArmJob?.cancel()
        noticeArmJob = null
        noticeArmToken = null
    }

    /** Push the wait notice. Only called once the delay has elapsed for a live calculation. */
    private fun pushCalculationNotice(calculation: RouteCalculation) {
        if (sessionDestroyed) return
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — calculation notice push deferred")
            return
        }
        if (!screenStack.needsCalculationNoticePush()) {
            // A notice landed while the delay ran (a previous push was deferred and re-applied):
            // update that one instead of pushing a second screen.
            applyCalculationNotice(navigationViewModel.state.value.calculation)
            return
        }
        val navState = navigationViewModel.state.value
        val notice = RouteCalculatingScreen(
            carContext = carContext,
            destinationName = navState.destinationName,
            percent = calculation.percent,
            // A reroute runs with guidance still live: it is neither cancelled nor popped away
            // from under the driver (spec: `auto/navigation-view` — Reroute keeps the navigation
            // view live under the notice; owner decision R2).
            cancellable = !navState.isNavigating,
            onCancel = { cancelCalculation() }
        )
        SessionLog.push("RouteCalculatingScreen")
        // A host-facing send is recorded like every other one (AGENTS.md diagnostics: the tag
        // HOST carries what the session sent the host) — this line is what makes the notice's
        // percentage verifiable on a device whose template tree no UI dump can see.
        calculationNoticeBucket = displayedCalculationPercent(calculation.percent)
        DiagnosticsLog.log(
            CAR_HOST_DIAG_TAG,
            "calculation notice push pct=$calculationNoticeBucket " +
                "cancellable=${!navState.isNavigating}"
        )
        val landed = guardedHostCall("push RouteCalculatingScreen") {
            carContext.getCarService(ScreenManager::class.java).push(notice)
        }
        // Only a landed push is recorded (spec: car-host-fault-isolation — Host screen-stack
        // mutations are balanced): a refused push must not claim a screen, and the next state
        // emission retries.
        if (landed) {
            calculationNotice = notice
            screenStack.onCalculationNoticePushed(notice)
        }
    }

    /**
     * Abort the calculation the notice describes. The engine clears its state, and the synced
     * notice leaves through [applyCalculationNotice] — so the removal has exactly one path and
     * cannot leave a screen behind (spec: `route-calculation-feedback` — Cancelling a
     * calculation aborts it and releases its resources).
     */
    private fun cancelCalculation() {
        guardedHostCall("cancel route calculation") { navigationViewModel.cancelAcquisition() }
    }

    /**
     * Remove the notice that is on the stack, or owe its removal to the next started period
     * (spec: car-host-fault-isolation — Bounded host-facing traffic while not visible).
     */
    private fun removeCalculationNotice() {
        val notice = calculationNotice ?: return
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — calculation notice removal deferred")
            screenStack.onCalculationNoticeDismissalDeferred()
            return
        }
        val removed = guardedHostCall("remove RouteCalculatingScreen") {
            carContext.getCarService(ScreenManager::class.java).remove(notice)
        }
        if (removed) {
            calculationNotice = null
            calculationNoticeBucket = null
            screenStack.onCalculationNoticeDismissed(notice)
        }
    }

    private fun showError(message: String) {
        if (sessionDestroyed) return
        if (!hostGate.allowHostMutation()) {
            Log.d(TAG, "session not started — error screen deferred")
            return
        }
        Log.d(TAG, "Showing error: $message")
        SessionLog.errorOverlay(message)

        val current = errorNotice
        if (current != null && !screenStack.needsErrorNoticePush()) {
            // A notice is already up: update it instead of stacking a second screen
            // (spec: car-host-fault-isolation — Host screen-stack mutations are balanced,
            // "Repeated errors").
            current.updateMessage(message)
            guardedHostCall("invalidate error notice") { current.invalidate() }
            scheduleErrorNoticeDismissal(current)
            return
        }

        val notice = ErrorOverlayScreen(carContext, message)
        val landed = guardedHostCall("push ErrorOverlayScreen") {
            carContext.getCarService(ScreenManager::class.java).push(notice)
        }
        if (landed) {
            errorNotice = notice
            screenStack.onErrorNoticePushed(notice)
            scheduleErrorNoticeDismissal(notice)
        }
    }

    /**
     * Auto-dismiss the error notice after [ERROR_DISPLAY_MS] (spec:
     * car-host-fault-isolation — Host screen-stack mutations are balanced; design D5).
     *
     * Two rules, both of which the previous `popToRoot()` dismissal broke: the pop is
     * scoped to **this notice** (`ScreenManager.remove` — a `popToRoot` took the navigation
     * view down with it while the session still believed it was shown), and a mutation that
     * would run after the session stopped or ended is **discarded** (spec — Bounded
     * host-facing traffic while not visible, "Deferred mutation after the session ended").
     * The local error state is cleared either way, so a skipped host call cannot pin the
     * error.
     */
    private fun scheduleErrorNoticeDismissal(notice: ErrorOverlayScreen) {
        errorDismissalJob?.cancel()
        errorDismissalJob = scope.launch(CoroutineName("error-dismissal")) {
            dismissErrorNotice(
                notice = notice,
                delayMs = ERROR_DISPLAY_MS,
                // Unconditional: a skipped host call must not pin the error (design D5).
                clearLocalError = { navigationViewModel.clearError() },
                // Destroyed, or a newer notice took this one's place.
                isSessionUsable = { !sessionDestroyed && errorNotice === notice },
                // The gate's `started`, not `allowHostMutation()`: asking the gate here would
                // record a host sync owed for a dismissal that is deferred anyway.
                hostMutationAllowed = { hostGate.started },
                removeNotice = { target ->
                    guardedHostCall("remove ErrorOverlayScreen") {
                        carContext.getCarService(ScreenManager::class.java)
                            .remove(target as ErrorOverlayScreen)
                    }
                },
                onRemoved = {
                    errorNotice = null
                    screenStack.onErrorNoticeDismissed(notice)
                },
                onDeferred = {
                    // No host call while the session is not started; the removal is owed to
                    // the next started sync.
                    screenStack.onErrorNoticeDismissalDeferred()
                    Log.d(TAG, "session not started — error notice dismissal deferred")
                },
                onSkipped = { reason -> Log.d(TAG, "error notice dismissal skipped: $reason") }
            )
        }
    }

    companion object {
        private const val TAG = "NavigationSession"
        private const val ERROR_DISPLAY_MS = 4000L

        /**
         * How long a route calculation may run before the car shows the wait notice. Provisional
         * until measured on a device (`ROUTE calc done:` entries — change
         * `show-route-calculation-progress`, task 5.3): a fast route must never flash a screen,
         * a long one must not leave the driver in silence.
         */
        private const val CALCULATION_NOTICE_DELAY_MS = 400L
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

/**
 * Identity-only message for a parsed car deep link (spec: auto-diagnostics —
 * Diagnostics carry no coordinates): the destination's shape, never the object —
 * [DeepLinkDestination]'s `toString()` prints the coordinates. Pure seam for unit
 * testing ([NavigationSession] cannot be constructed in Robolectric).
 */
internal fun deepLinkLogMessage(destination: DeepLinkDestination): String =
    "Deep link parsed: shape=${if (destination.hasCoordinates) "coordinates" else "query"}"

/**
 * Whether the notice that currently shows [showingBucket] has to be refreshed for a
 * [reportedPercent]: only a changed *display* step is worth a host push — the same rule the
 * distance bucket applies to the navigation template (spec: `car-host-fault-isolation` —
 * Bounded host-facing traffic; design D5). Pure seam for unit testing
 * ([NavigationSession] cannot be constructed in Robolectric).
 */
internal fun noticeNeedsUpdate(showingBucket: Int?, reportedPercent: Int?): Boolean =
    showingBucket != displayedCalculationPercent(reportedPercent)

/**
 * Whether the car wait notice has to be armed for [live]: a calculation is in flight, no notice
 * is up, and no delay for exactly this calculation is already running — a second percentage
 * update must not restart the delay (spec: `route-calculation-feedback` — Car wait notice while
 * a route is being calculated). Pure seam for unit testing ([NavigationSession] cannot be
 * constructed in Robolectric).
 */
internal fun needsNoticeArm(
    live: RouteCalculation?,
    noticeShown: Boolean,
    armedToken: Long?,
    arming: Boolean
): Boolean =
    live != null && !noticeShown && !(arming && armedToken == live.token)

/**
 * Whether the notice on the stack belongs to a calculation that is no longer in flight: it has
 * to leave — the notice never outlives what it describes, and a calculation that ended while the
 * session was stopped must not leave one behind (spec: `route-calculation-feedback` — The car
 * notice never outlives its calculation). Pure seam for unit testing.
 */
internal fun noticeIsStale(live: RouteCalculation?, noticeShown: Boolean): Boolean =
    live == null && noticeShown

/**
 * Whether the delay armed for [armedToken] may still raise its notice: the engine must still
 * report exactly that calculation in flight. Pure seam for unit testing.
 */
internal fun noticeIsStillArmedFor(armedToken: Long?, live: RouteCalculation?): Boolean =
    live != null && live.token == armedToken

/**
 * Pure mapping from a host [Configuration] to the night-mode flag. Extracted
 * for unit testing — [NavigationSession] itself needs a host-provided
 * [androidx.car.app.CarContext] and cannot be constructed in Robolectric.
 */
internal fun isNightUiMode(configuration: Configuration): Boolean =
    configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

/**
 * Whether a restored session should push the free-driving view (design D1,
 * spec: auto/free-driving — session restore). The free-driving and navigation
 * modes are mutually exclusive: navigation is restored by the observer's state
 * emission, free driving by an explicit push — never both, and never as the
 * session root. Pure seam for unit testing ([NavigationSession] cannot be
 * constructed in Robolectric).
 */
internal fun shouldRestoreFreeDriving(isNavigating: Boolean, freeDrivingActive: Boolean): Boolean =
    freeDrivingActive && !isNavigating

/**
 * Whether the end of the car session ends the navigation it presented (spec:
 * `auto/navigation-view` — Navigation ends when the car session ends after arrival): the session
 * was driving and had reached its destination. The arrival fact lives in the shared navigation
 * state, so it is read *after* the fact, at the moment the session is gone — no surface state and
 * no host callback participates. Arriving while the session is live is not a stop: the driver may
 * still be looking for a parking spot. Pure seam for unit testing ([NavigationSession] cannot be
 * constructed in Robolectric).
 */
internal fun shouldEndNavigationOnSessionEnd(state: NavigationState): Boolean =
    state.isNavigating && state.hasReachedDestination

/**
 * Perform that decision: stop the navigation the ended session was driving, and report whether it
 * was ended. The call is the half with a consequence, so it has its own seam — a unit test drives
 * it with a fake [stop] instead of leaving it reachable only on a device. Confinement is the
 * caller's job ([guardedHostCall]): an already-gone host must not let a fault escape the lifecycle
 * callback.
 */
internal fun endNavigationAfterArrival(state: NavigationState, stop: () -> Unit): Boolean {
    if (!shouldEndNavigationOnSessionEnd(state)) return false
    stop()
    return true
}

/**
 * The end decision as one diagnostics line (spec: `auto/navigation-view` — End decision is
 * diagnosable without coordinates): identity and numbers only — was the session driving, was the
destination reached, the remaining distance in whole metres, and what the session did about it.
 * Pure seam for unit testing.
 */
internal fun sessionEndNavigationDecisionMessage(state: NavigationState, ended: Boolean): String =
    "Session end: navigating=${state.isNavigating} reached=${state.hasReachedDestination}" +
        " remaining=${state.remainingDistance.toInt()}m -> navigation " +
        (if (ended) "ended" else "kept")

/**
 * Trip factory used by the car session's trip publisher (spec:
 * auto-navigation-hints — "Trip metadata for cluster and heads-up display"):
 * the template's maneuver artwork and the car string resolver, so the rail
 * card, the cluster step and the notification hint cannot disagree. Pure seam
 * for unit testing ([NavigationSession] cannot be constructed in Robolectric).
 */
internal fun carTripFor(state: NavigationState, resolver: StringResolver): Trip? =
    NavigationTemplateMapper.tripFromState(state, ManeuverGlyphs::forTurnType, resolver)

/**
 * Resolve the car dark presentation from the shared dark mode preference
 * ("ON"/"OFF"/"AUTOMATIC") and the host day/night signal: ON always dark,
 * OFF always light, AUTOMATIC (and any unknown value) follows the host.
 * Extracted from [NavigationSession.resolvedDark] for unit testing.
 */
internal fun resolveCarDark(pref: String, hostDark: Boolean): Boolean = when (pref) {
    "ON" -> true
    "OFF" -> false
    else -> hostDark
}
