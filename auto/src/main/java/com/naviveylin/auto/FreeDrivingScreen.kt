package com.naviveylin.auto

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Android Auto free-driving screen (spec: auto/free-driving): a
 * destination-free [NavigationTemplate] showing a heading-up,
 * vehicle-following map with the GPS marker, compass rose, and current street
 * name. No turn-by-turn hints, no lane guidance, no travel estimate — there
 * is no target.
 *
 * Pushed on top of the map screen from the "Free driving" menu row; BACK and
 * the "Exit" action pop back to the map view. The screen never touches the
 * session's `isNavigating` state machine (design D1).
 */
class FreeDrivingScreen(
    carContext: CarContext
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var streetJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()
    private val client by lazy { entryPoint.autoClientProvider().client() }

    private val streetNameUpdater = StreetNameUpdater()
    private val autoZoomController = AutoZoomController()

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    // Host-reported stable area (surface pixels; empty = unknown): the street
    // label and compass rose stay inside it so host chrome never covers them.
    private val stableArea = Rect()

    /** Current street name label text; null/blank = nothing drawn. */
    @Volatile
    private var streetName: String? = null

    /** Latest heading in radians for the compass rose and viewport; null until a fix. */
    @Volatile
    private var headingRadians: Double? = null

    /** Latest ground speed (km/h) for the speed readout; NaN when unknown. */
    @Volatile
    private var currentSpeedKmH: Double = Double.NaN

    /** Latest road speed limit (km/h) for the limit sign; NaN when undefined. */
    @Volatile
    private var maxSpeedKmH: Double = Double.NaN

    /** Last derived speed (km/h), kept when a fix is too close to derive from. */
    private var lastEffectiveSpeed = Double.NaN

    /** Auto-zoom from the shared settings (default on). */
    @Volatile
    private var autoZoomEnabled: Boolean = true

    /** Last fix position + effective bearing, for GPX replay without a GPS bearing. */
    private var lastFixLat = Double.NaN
    private var lastFixLon = Double.NaN
    private var lastFixTimeMs = 0L
    private var lastEffectiveBearing = Double.NaN

    private val mapRenderer: AutoMapRenderer by lazy {
        val renderDpi = carContext.resources.displayMetrics.densityDpi.toDouble()
        AutoMapRenderer(
            client,
            renderDpi,
            DEFAULT_LAT,
            DEFAULT_LON,
            DEFAULT_AA_ZOOM
        )
    }

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        // BACK and the system back action both leave free driving (spec:
        // "Exit free driving" — back returns to the map view).
        enableBackNavigation()

        // Shared settings: auto-zoom (speed → magnification) applies live.
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { settings ->
                    autoZoomEnabled = settings.autoZoomEnabled
                    Log.d(TAG, "settings loaded: autoZoomEnabled=$autoZoomEnabled")
                }
                .onFailure { Log.w(TAG, "loading settings failed", it) }
        }

        // Surface overlays: compass rose (rotates with the map) + current
        // speed readout below it (badge shows the current speed — free driving
        // has no speed-limit data) + the street-name label at the bottom. No
        // navigation hint panel — free driving has no target.
        mapRenderer.overlayDrawer = { canvas, w, h ->
            val density = (surfaceDpi / 160.0).toFloat()
            SurfaceIndicators.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                stableBounds = stableArea,
                density = density,
                angleRadians = headingRadians ?: 0.0,
                currentKmH = currentSpeedKmH,
                maxKmH = maxSpeedKmH,
                drawSpeedLimitSign = true
            )
            StreetNameLabel.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                usableBounds = stableArea,
                name = streetName.orEmpty()
            )
        }

        // If the host delivered a surface we cannot lock (AAOS emulator quirk:
        // it locks surfaces it re-delivers after a screen transition), drop it
        // and ask the host for a fresh one. Throttled inside the renderer and
        // capped per screen start so a persistently-bad surface does not
        // invalidate the template forever.
        mapRenderer.onSurfaceFailed = {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
            ) {
                surfaceRefreshAttempts++
                Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                invalidate()
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                registerSurfaceCallback()
                mapRenderer.resume()
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
                mapRenderer.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start.
                mapRenderer.releaseSurface()
                // NOTE: no unregisterSurfaceCallback() here. The car-app
                // library starts the new screen BEFORE stopping the old one
                // (ScreenManager.pushInternal), so an onStop unregister would
                // null the surface callback the new screen just registered —
                // the host then re-delivers a second surface and locks both
                // (IAE on every lockCanvas). Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                stopObserving()
                unregisterSurfaceCallback()
                mapRenderer.shutdown()
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        return try {
            buildTemplate()
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "FreeDrivingTemplate build failed", e)
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun buildTemplate(): NavigationTemplate {
        // NavigationTemplate: the ONLY template with no content slot — the map
        // fills the surface, no host-rendered panel/box (MapWithContentTemplate
        // showed a "Free driving" box, MapTemplate a "No items" placeholder).
        // The earlier belief that the emulator host locks the navigation
        // surface predates the renderer fixes (the old drawToSurface had no
        // finally-unlock, so one draw exception leaked the lock and every
        // later lockCanvas threw "already locked"); with the release/isValid/
        // invalidate fixes the surface renders. No NavigationInfo is passed,
        // so the host renders the bare map; free driving never touches the
        // session's isNavigating state machine (design D1). The host draws its
        // own compass — cosmetic overlap with the app's rose, accepted.
        return MapTemplateFactory.buildFullScreenTemplate(
            mapActionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.stopAction { exitFreeDriving() })
                .build(),
            actionStrip = ActionStrip.Builder()
                .addAction(NavigationScreenActions.zoomInAction { onZoomIn() })
                .addAction(NavigationScreenActions.zoomOutAction { onZoomOut() })
                .build()
        )
    }

    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            locationProvider.position().collect { pos ->
                if (pos != null) {
                    // A single bad fix must never kill the collect flow —
                    // otherwise the marker freezes at the last good fix.
                    runCatching { onGpsFix(pos) }
                        .onFailure { Log.w(TAG, "onGpsFix failed", it) }
                }
            }
        }
    }

    /**
     * Apply a GPS fix: marker, heading-up rotation, auto-zoom, street name.
     * GPX replay tracks usually lack a GPS bearing — fall back to the
     * movement direction between fixes so heading-up still engages.
     */
    private fun onGpsFix(pos: AutoPosition) {
        // Speed first: it reads the previous fix position, which
        // effectiveBearing overwrites below.
        val speed = effectiveSpeed(pos)
        val bearing = effectiveBearing(pos)
        Log.d(
            TAG,
            "GPS fix lat=${pos.lat} lon=${pos.lon} bearing=${pos.bearing} " +
                "effBearing=$bearing speedKmH=${pos.speedKmH} effSpeed=$speed"
        )
        currentSpeedKmH = speed
        mapRenderer.setGpsMarker(pos.lat, pos.lon, bearing, pos.accuracy)

        // Heading-up always (spec: "Heading-up orientation"), independent of
        // the shared navNorthUp setting.
        val angle = headingAngleRadians(bearing)
        if (angle != null) {
            headingRadians = angle
        }
        // Speed-driven auto-zoom (shared setting; speed → magnification).
        val newZoom = if (autoZoomEnabled) autoZoomController.onSpeed(speed) else null

        if (angle != null || newZoom != null) {
            // setViewport disengages follow mode, so re-engage + re-center
            // right after (same pattern as NavigationScreen).
            val vp = mapRenderer.viewportState.value
            val zoom = newZoom ?: vp.zoom
            mapRenderer.setViewport(vp.lat, vp.lon, zoom, angle ?: vp.angle, zoom.toDouble())
            mapRenderer.reCenter()
            if (newZoom != null) {
                Log.d(TAG, "autoZoom commit speed=${pos.speedKmH} mag=$newZoom")
            }
        }

        resolveStreetName(pos)
    }

    /**
     * Effective speed: GPS speed when valid, else derived from the movement
     * between consecutive fixes (GPX replay tracks usually carry no speed).
     */
    private fun effectiveSpeed(pos: AutoPosition): Double {
        val nowMs = System.currentTimeMillis()
        val derived = if (pos.speedKmH >= 0.0) {
            pos.speedKmH
        } else {
            movementSpeedKmH(lastFixLat, lastFixLon, pos.lat, pos.lon, nowMs - lastFixTimeMs)
                ?: lastEffectiveSpeed
        }
        if (derived >= 0.0) lastEffectiveSpeed = derived
        lastFixTimeMs = nowMs
        return derived
    }

    /**
     * Effective bearing: GPS bearing when valid, else movement direction
     * between consecutive fixes (null when moved too little — keep the last
     * effective bearing so the map does not snap back to north-up).
     */
    private fun effectiveBearing(pos: AutoPosition): Double {
        val derived = if (pos.bearing >= 0.0) {
            pos.bearing
        } else {
            movementBearing(lastFixLat, lastFixLon, pos.lat, pos.lon) ?: lastEffectiveBearing
        }
        if (derived >= 0.0) lastEffectiveBearing = derived
        lastFixLat = pos.lat
        lastFixLon = pos.lon
        return derived
    }

    /**
     * Throttled reverse geocode + speed-limit lookup (design D4): native calls
     * off the main thread; on success update/clear the label and the limit
     * sign and mark the position as geocoded — on failure keep the last
     * values.
     */
    private fun resolveStreetName(pos: AutoPosition) {
        if (!streetNameUpdater.shouldGeocode(pos.lat, pos.lon)) return
        if (streetJob?.isActive == true) return
        streetJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val address = client.getAddressAt(pos.lat, pos.lon)
                    val maxSpeed = client.getMaxSpeedAt(pos.lat, pos.lon)
                    address to maxSpeed
                } catch (e: Exception) {
                    Log.w(TAG, "road info lookup failed", e)
                    null
                }
            }
            if (result != null) {
                streetName = streetNameUpdater.streetFromAddress(result.first)
                // getMaxSpeedAt returns negative when the road has no limit —
                // hide the sign (spec: "No sign without a limit").
                maxSpeedKmH = result.second.takeIf { it > 0.0 } ?: Double.NaN
                streetNameUpdater.markGeocoded(pos.lat, pos.lon)
                mapRenderer.requestRender()
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun exitFreeDriving() {
        Log.d(TAG, "Exit free driving")
        screenManager.pop()
    }

    private fun onZoomIn() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        Log.d(TAG, "free driving zoom+ $zoom (auto-zoom suspended)")
        mapRenderer.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        mapRenderer.reCenter()
    }

    private fun onZoomOut() {
        autoZoomController.suspend()
        val vp = mapRenderer.viewportState.value
        val zoom = (vp.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        Log.d(TAG, "free driving zoom- $zoom (auto-zoom suspended)")
        mapRenderer.setViewport(vp.lat, vp.lon, zoom, vp.angle, zoom.toDouble())
        mapRenderer.reCenter()
    }

    private fun registerSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(object : SurfaceCallback {
            override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                val surface = surfaceContainer.surface ?: return
                surfaceWidth = surfaceContainer.width
                surfaceHeight = surfaceContainer.height
                surfaceDpi = surfaceContainer.dpi.takeIf { it > 0 }?.toDouble() ?: DEFAULT_DPI
                Log.d(TAG, "Free driving surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
                DiagnosticsLog.log(
                    "FREEDRIVE",
                    "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
                )
                runCatching { client.setMapDpi(surfaceDpi) }
                    .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                mapRenderer.updateProjectionDpi(surfaceDpi)
                mapRenderer.onSurfaceCreated(surface, surfaceWidth, surfaceHeight)
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Free driving surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                mapRenderer.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visibleArea: Rect) {
                if (stableArea.isEmpty()) stableArea.set(visibleArea)
                mapRenderer.requestRender()
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                stableArea.set(newStableArea)
                mapRenderer.requestRender()
            }
        })
    }

    private fun unregisterSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(null)
    }

    companion object {
        private const val TAG = "FreeDrivingScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

        /** Fallback center (Dortmund — same as the phone app default). */
        private const val DEFAULT_LAT = 51.5136
        private const val DEFAULT_LON = 7.4653

        /** City-level zoom for the surface before GPS arrives. */
        private const val DEFAULT_AA_ZOOM = 13

        /**
         * Heading-up viewport rotation in radians; null when the bearing is
         * unknown (NaN) or invalid (< 0), so the previous heading is kept.
         * NEGATIVE bearing: the native/Kotlin projections rotate by R(-angle),
         * so angle = -bearing makes the driving direction point up (same
         * convention as the phone app, MapCanvasViewModel). Extracted for
         * unit tests (spec: "Heading-up orientation").
         */
        internal fun headingAngleRadians(bearingDegrees: Double): Double? =
            if (bearingDegrees >= 0.0) -Math.toRadians(bearingDegrees) else null

        /** Minimum movement (m) before a derived bearing is trusted. */
        private const val MIN_BEARING_MOVE_M = 3.0

        /**
         * Bearing (degrees 0..360, clockwise from north) of the movement from
         * (lat1,lon1) to (lat2,lon2); null when the movement is below
         * [MIN_BEARING_MOVE_M] (too noisy to trust). Used for GPX replay
         * tracks without a GPS bearing.
         */
        internal fun movementBearing(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double? {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val midLat = Math.toRadians((lat1 + lat2) / 2.0)
            val x = dLon * cos(midLat)
            val dist = EARTH_RADIUS_M * sqrt(dLat * dLat + x * x)
            if (dist < MIN_BEARING_MOVE_M) return null
            var deg = Math.toDegrees(Math.atan2(x, dLat))
            if (deg < 0.0) deg += 360.0
            return deg
        }

        private const val EARTH_RADIUS_M = 6371000.0

        /** Minimum time (ms) between fixes before a derived speed is trusted. */
        private const val MIN_SPEED_DT_MS = 500L

        /** Minimum movement (m) before a derived speed is trusted. */
        private const val MIN_SPEED_MOVE_M = 1.0

        /**
         * Speed (km/h) implied by the movement from (lat1,lon1) to
         * (lat2,lon2) over [dtMs]; null when the time delta or movement is
         * too small to trust. Used for GPX replay tracks without a GPS speed.
         */
        internal fun movementSpeedKmH(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
            dtMs: Long
        ): Double? {
            if (dtMs < MIN_SPEED_DT_MS) return null
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val midLat = Math.toRadians((lat1 + lat2) / 2.0)
            val x = dLon * cos(midLat)
            val dist = EARTH_RADIUS_M * sqrt(dLat * dLat + x * x)
            if (dist < MIN_SPEED_MOVE_M) return null
            return dist / (dtMs / 1000.0) * 3.6
        }
    }
}
