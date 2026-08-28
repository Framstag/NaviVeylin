@file:Suppress("DEPRECATION") // MapTemplate: no content-free map template exists in the car-app API

package com.naviveylin.auto

import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ActionStrip
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.PanModeListener
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.InstalledMaps
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Android Auto screen that displays a libosmscout-rendered map via [MapTemplate].
 *
 * Uses [SurfaceCallback] (registered via [AppManager]) to receive a [android.view.Surface]
 * for [AutoMapRenderer] to draw on. Gesture handling (pan, zoom, click) is done through
 * [SurfaceCallback] methods.
 *
 * Supports destination selection: tap on map → details overlay with "Navigate to" action.
 *
 * Note: [MapTemplate] (deprecated in favor of [androidx.car.app.navigation.model.MapWithContentTemplate])
 * is used deliberately — a full-screen map without a content overlay. The replacement requires
 * a content template (List/Pane/Grid/Message) and would reserve screen space for it.
 */
class MapScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    /** Center the map here instead of the saved viewport / map bbox (details "Show" action). */
    private val initialCenter: Pair<Double, Double>? = null,
    /** Zoom for [initialCenter]; ignored when [initialCenter] is null. */
    private val initialZoom: Int = DEFAULT_AA_ZOOM,
    /** Destination name for the marker when [initialCenter] is set (details "Show" action). */
    private val initialDestinationName: String? = null
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()

    /** Applies the shared map style to the native client (deduped). */
    private val styleApplier = CarStyleApplier { style ->
        entryPoint.autoClientProvider().client().loadStyleSheet(style)
    }

    /**
     * Initial viewport for the renderer: last phone-app viewport, else the
     * first installed map's bounding box, else the global default. Picked at
     * city zoom so downloaded map data is visible immediately.
     */
    private data class InitialViewport(val lat: Double, val lon: Double, val zoom: Int)

    private val initialViewport: InitialViewport by lazy { computeInitialViewport() }

    private val mapRenderer: AutoMapRenderer by lazy {
        val clientProvider = entryPoint.autoClientProvider()
        val client = clientProvider.client()
        // The native renderer projects with the client's configured physical
        // DPI (from the phone display metrics), not the car surface DPI — all
        // overlay math (gestures, GPS marker) must use the same value.
        val renderDpi = carContext.resources.displayMetrics.densityDpi.toDouble()
        AutoMapRenderer(
            client,
            renderDpi,
            initialViewport.lat,
            initialViewport.lon,
            initialViewport.zoom,
            // "Show location" maps must stay on the requested destination —
            // follow mode would snap the viewport to every GPS fix.
            initialFollowMode = initialCenter == null
        )
    }

    private var mapController: MapController? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    private var lastGestureLogMs = 0L
    private var lastSettingsReloadMs = 0L

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        // Browse mode has no surface overlays: the host on this class of unit
        // never forwards surface gestures, so all interactive controls live in
        // host action strips. (The compass rose is navigation-only.)

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
                // NOTE: no unregisterSurfaceCallback() here — see
                // FreeDrivingScreen: an onStop unregister nulls the callback
                // the next screen registered (car-app starts the new screen
                // before stopping the old one), forcing a second surface
                // delivery that the host locks. Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterSurfaceCallback()
                mapRenderer.shutdown()
                scope.cancel()
            }
        })

        // Initial settings: follow mode + browse orientation apply live.
        scope.launch {
            runCatching { settingsProvider.load() }
                .onSuccess { applySettings(it) }
                .onFailure { Log.w(TAG, "initial settings load failed", it) }
        }
    }

    /**
     * Apply shared settings that affect the browse map live: follow mode
     * (re-center on GPS) and north-up orientation. Settings are re-read
     * periodically so changes from the settings screen take effect without a
     * screen restart.
     */
    private fun applySettings(settings: com.naviveylin.core.AutoSettings) {
        // When opened via the details "Show" action (initialCenter set), the
        // map must stay centered on the requested destination — follow mode
        // would snap it back to the current GPS position.
        if (settings.followMode && !mapRenderer.isFollowMode() && initialCenter == null) {
            Log.d(TAG, "settings: follow mode on -> re-center")
            mapRenderer.reCenter()
        }
        if (settings.freeFormNorthUp) {
            val vp = mapRenderer.viewportState.value
            if (vp.angle != 0.0) {
                Log.d(TAG, "settings: north-up -> reset angle")
                mapRenderer.setViewport(vp.lat, vp.lon, vp.zoom, 0.0, vp.zoom.toDouble())
            }
        }
        // Apply the shared map style (loadStyleSheet blocks on the native DB
        // thread — run off the main thread; deduped by the applier).
        val style = settings.styleSheet
        scope.launch(Dispatchers.Default) {
            if (!styleApplier.apply(style)) {
                Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
            }
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            val template = buildTemplate()
            DiagnosticsLog.log("MAP", "MapTemplate delivered")
            template
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "MapTemplate build failed", e)
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun computeInitialViewport(): InitialViewport {
        initialCenter?.let { (lat, lon) ->
            Log.d(TAG, "Show map: initialCenter=$lat,$lon zoom=$initialZoom")
            return InitialViewport(lat, lon, initialZoom)
        }
        latestSavedViewport()?.let { return it }
        firstInstalledMapBbox()?.let { bbox ->
            return InitialViewport(
                (bbox[0] + bbox[2]) / 2.0,
                (bbox[1] + bbox[3]) / 2.0,
                DEFAULT_AA_ZOOM
            )
        }
        return InitialViewport(DEFAULT_LAT, DEFAULT_LON, DEFAULT_AA_ZOOM)
    }

    /** Most recently modified phone-app viewport (`maps/viewport-*.json`). */
    private fun latestSavedViewport(): InitialViewport? {
        return try {
            val mapsDir = File(carContext.filesDir, "maps")
            val file = mapsDir.listFiles { f ->
                f.isFile && f.name.startsWith("viewport-") && f.name.endsWith(".json")
            }?.maxByOrNull { it.lastModified() } ?: return null
            val json = JSONObject(file.readText())
            val lat = json.optDouble("centerLat", Double.NaN)
            val lon = json.optDouble("centerLon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null
            val mag = json.optInt("magnification", DEFAULT_AA_ZOOM)
                .coerceIn(AutoMapRenderer.MIN_ZOOM, AutoMapRenderer.MAX_ZOOM)
            Log.d(TAG, "initial viewport from saved ${file.name}: $lat,$lon mag=$mag")
            InitialViewport(lat, lon, mag)
        } catch (e: Exception) {
            Log.w(TAG, "latestSavedViewport failed", e)
            null
        }
    }

    /** Bounding box of the first installed (non-basemap) map database. */
    private fun firstInstalledMapBbox(): DoubleArray? {
        return try {
            val mapsDir = File(carContext.filesDir, "maps")
            // Shared with the phone app + AA warmup (InstalledMaps): recursive
            // scan for database directories, excluding the basemap overlay.
            val dirs = InstalledMaps.findDatabaseDirectories(
                mapsDir.absolutePath,
                File(mapsDir, "basemap").absolutePath
            ).sorted()
            if (dirs.isEmpty()) return null
            val client = entryPoint.autoClientProvider().client()
            for (dir in dirs) {
                try {
                    val bbox = client.getDatabaseBoundingBox(dir)
                    if (bbox != null && bbox.size >= 4) {
                        Log.d(TAG, "initial viewport from map ${File(dir).name} bbox ${bbox.toList()}")
                        return bbox
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "bbox for ${File(dir).name} failed", e)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun buildTemplate(): MapWithContentTemplate {
        // No map action strip: the content menu covers app navigation; search
        // + settings + zoom live in the right template strip. The host caps
        // the strip at 4 actions (ACTIONS_CONSTRAINTS_MAP), so licence info
        // stays in the content menu's About row instead of a strip action.
        // PanModeListener: the host only forwards pan gestures to the surface
        // while pan mode is active (AAOS/AA hosts render a pan affordance when
        // the listener is registered).
        mapController = MapController.Builder()
            .setPanModeListener(object : PanModeListener {
                override fun onPanModeChanged(panMode: Boolean) {
                    Log.d(TAG, "pan mode: $panMode")
                    if (panMode) {
                        // Disengage follow so the map does not snap back to GPS.
                        val vp = mapRenderer.viewportState.value
                        mapRenderer.setViewport(vp.lat, vp.lon, vp.zoom, vp.angle, vp.zoom.toDouble())
                    }
                }
            })
            .build()

        // Content box = app menu.
        val content = MapTemplateFactory.buildMenuContent(
            onFreeDriving = {
                // Push the destination-free navigation-style view (spec:
                // auto/free-driving). Popping it returns to this map view.
                screenManager.push(FreeDrivingScreen(carContext))
            },
            onStarredFavorites = {
                screenManager.push(FavoritesScreen(carContext, navigationViewModel, starredOnly = true))
            },
            onAllFavorites = {
                screenManager.push(FavoritesScreen(carContext, navigationViewModel))
            },
            onPoiSearch = {
                screenManager.push(PoiSearchScreen(carContext, navigationViewModel))
            },
            onSearchHistory = {
                screenManager.push(SearchHistoryScreen(carContext, navigationViewModel))
            },
            onDiagnostics = {
                screenManager.push(DiagnosticsScreen(carContext))
            },
            onAbout = {
                screenManager.push(AboutScreen(carContext))
            }
        )

        // Right edge: Search (parked-only), Settings (driving-safe), zoom
        // (host strip — the only reliably tappable chrome on this class of
        // unit; surface gestures are not forwarded).
        val actionStrip = ActionStrip.Builder()
            .addAction(MapStripActions.searchAction {
                screenManager.push(SearchScreen(carContext, navigationViewModel))
            })
            .addAction(MapStripActions.settingsAction {
                screenManager.push(PreferencesScreen(carContext))
            })
            .addAction(MapStripActions.zoomInAction { onZoomIn() })
            .addAction(MapStripActions.zoomOutAction { onZoomOut() })
            .build()

        return MapTemplateFactory.buildTemplate(mapController!!, content, actionStrip)
    }

    private fun registerSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(object : SurfaceCallback {
            override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                val surface = surfaceContainer.surface ?: return
                surfaceWidth = surfaceContainer.width
                surfaceHeight = surfaceContainer.height
                surfaceDpi = surfaceContainer.dpi.takeIf { it > 0 }?.toDouble() ?: DEFAULT_DPI
                Log.d(TAG, "Map surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
                DiagnosticsLog.log(
                    "MAP",
                    "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
                )
                // Render at the CAR display's DPI — the client is built with
                // the phone metrics, which would scale the map ~1.8x too
                // zoomed on a ~236-dpi head unit. Takes effect on the next
                // render.
                runCatching { entryPoint.autoClientProvider().client().setMapDpi(surfaceDpi) }
                    .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                mapRenderer.updateProjectionDpi(surfaceDpi)
                mapRenderer.onSurfaceCreated(surface, surfaceWidth, surfaceHeight)
                // "Show location" maps: draw the destination marker and center
                // it in the visible map area (the host's menu panel covers the
                // left ~40% of the surface).
                initialCenter?.let { (clat, clon) ->
                    mapRenderer.setDestinationMarker(clat, clon, initialDestinationName)
                    val rtl = carContext.resources.configuration.layoutDirection ==
                        View.LAYOUT_DIRECTION_RTL
                    val (vlat, vlon) = paneOffsetCenter(
                        clat, clon, initialZoom,
                        surfaceWidth, surfaceHeight, surfaceDpi, rtl
                    )
                    mapRenderer.setViewport(vlat, vlon, initialZoom, 0.0)
                }
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Map surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                mapRenderer.onSurfaceDestroyed()
            }

            override fun onScroll(distanceX: Float, distanceY: Float) {
                // Convert scroll to viewport change. Use the same DPI as the
                // native render (not the surface DPI) so the pan tracks the
                // finger 1:1. Positive deltas move the map with the finger.
                val vp = mapRenderer.viewportState.value
                val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                    distanceX.toDouble(), distanceY.toDouble(),
                    vp.angle,
                    vp.zoom,
                    surfaceWidth.toDouble(), surfaceHeight.toDouble(),
                    vp.lat, vp.lon,
                    mapRenderer.projectionDpi
                )
                Log.d(
                    TAG,
                    "onScroll dx=$distanceX dy=$distanceY center=${vp.lat},${vp.lon} mag=${vp.zoom} -> $newLat,$newLon"
                )
                // Mirror to the file-backed diagnostics log (throttled) so pan
                // behavior is visible even when logcat capture misses the app.
                val now = System.currentTimeMillis()
                if (now - lastGestureLogMs > GESTURE_LOG_INTERVAL_MS) {
                    lastGestureLogMs = now
                    DiagnosticsLog.log(
                        "MAP",
                        "onScroll dx=$distanceX dy=$distanceY center=${vp.lat},${vp.lon} mag=${vp.zoom} -> $newLat,$newLon"
                    )
                }
                mapRenderer.setViewport(newLat, newLon, vp.zoom, vp.angle)
            }

            override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
                // Ignore jitter so tiny finger spread during a pan never triggers a zoom step
                if (abs(scaleFactor - 1f) < SCALE_JITTER_THRESHOLD) return

                val vp = mapRenderer.viewportState.value
                // Continuous zoom accumulation: each onScale event nudges the
                // fractional zoom by log2(scaleFactor); only whole-level changes render.
                val (newFraction, newZoom) = mapRenderer.zoomStep(scaleFactor)
                if (newZoom == vp.zoom && newFraction == mapRenderer.fractionalZoom()) return

                // Host may report an unavailable focal point (negative coords, e.g.
                // rotary-knob zoom) — zoom around the screen center in that case.
                val fx = if (focusX >= 0f) focusX.toDouble() else surfaceWidth / 2.0
                val fy = if (focusY >= 0f) focusY.toDouble() else surfaceHeight / 2.0
                val (newLat, newLon) = ProjectionUtils.zoomAtCursor(
                    fx, fy,
                    vp.zoom, newZoom,
                    surfaceWidth.toDouble(), surfaceHeight.toDouble(),
                    vp.lat, vp.lon,
                    mapRenderer.projectionDpi
                )
                Log.d(TAG, "onScale focus=($fx,$fy) factor=$scaleFactor -> mag=$newZoom center=$newLat,$newLon")
                mapRenderer.setViewport(newLat, newLon, newZoom, vp.angle, newFraction)
            }

            override fun onClick(x: Float, y: Float) {
                // Convert screen coordinates to geo coordinates
                val vp = mapRenderer.viewportState.value
                val (lat, lon) = ProjectionUtils.screenToGeo(
                    x.toDouble(), y.toDouble(),
                    surfaceWidth, surfaceHeight,
                    vp.zoom, vp.lat, vp.lon,
                    mapRenderer.projectionDpi
                )
                Log.d(TAG, "onClick ($x,$y) -> $lat,$lon mag=${vp.zoom}")
                onLocationSelected(lat, lon)
            }
        })
    }

    private fun unregisterSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(null)
    }

    /**
     * Called when a location is selected on the map (via tap or favorite marker).
     * Queries the ranked candidate objects at the point off the main thread:
     * several candidates → push the candidate picker; exactly one → details
     * screen directly with that description; none → details screen with
     * coordinates (existing behavior).
     */
    private fun onLocationSelected(lat: Double, lon: Double) {
        val client = entryPoint.autoClientProvider().client()
        val mag = mapRenderer.viewportState.value.zoom
        val screenManager = carContext.getCarService(ScreenManager::class.java)

        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val candidates = withContext(Dispatchers.Default) {
                try {
                    client.getDescriptionCandidates(lat, lon, mag)
                } catch (e: Exception) {
                    Log.w(TAG, "getDescriptionCandidates failed", e)
                    emptyList()
                }
            }
            val screen = if (shouldShowCandidatePicker(candidates.size)) {
                CandidatePickerScreen(carContext, candidates) { desc ->
                    screenManager.push(
                        DetailsScreen(
                            carContext, navigationViewModel, lat, lon,
                            preloadedDescription = desc,
                            mag = mag
                        )
                    )
                }
            } else {
                DetailsScreen(
                    carContext, navigationViewModel, lat, lon,
                    preloadedDescription = candidates.firstOrNull(),
                    mag = mag
                )
            }
            screenManager.push(screen)
        }
    }


    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            // GPS position: prefer the AA location provider (the AA-only
            // process has no phone UI mirroring into navigationViewModel).
            locationProvider.position().collect { pos ->
                if (pos != null) {
                    mapRenderer.setGpsMarker(pos.lat, pos.lon, pos.bearing, pos.accuracy)
                    // Periodic settings refresh so changes made in the settings
                    // screen take effect live (no flow on the provider yet).
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSettingsReloadMs > SETTINGS_RELOAD_INTERVAL_MS) {
                        lastSettingsReloadMs = now
                        scope.launch {
                            runCatching { settingsProvider.load() }
                                .onSuccess { applySettings(it) }
                                .onFailure { Log.w(TAG, "settings reload failed", it) }
                        }
                    }
                }
            }
        }

        // Observe favorites for markers
        scope.launch {
            favoritesProvider.favoriteLocations().collect { favorites ->
                val allFavorites = favorites.values.flatten()
                mapRenderer.setFavoriteLocations(allFavorites)
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun onZoomIn() {
        val current = mapRenderer.viewportState.value
        val zoom = (current.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        mapRenderer.setViewport(current.lat, current.lon, zoom, current.angle, zoom.toDouble())
    }

    private fun onZoomOut() {
        val current = mapRenderer.viewportState.value
        val zoom = (current.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        mapRenderer.setViewport(current.lat, current.lon, zoom, current.angle, zoom.toDouble())
    }

    companion object {
        private const val TAG = "MapScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val SURFACE_WIDTH = 1920
        private const val SURFACE_HEIGHT = 1080
        private const val DEFAULT_DPI = 160.0
        private const val SCALE_JITTER_THRESHOLD = 0.02f

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2

        /** Fallback center (Dortmund — same as the phone app default). */
        private const val DEFAULT_LAT = 51.5136
        private const val DEFAULT_LON = 7.4653

        /** City-level zoom so downloaded map data is visible immediately. */
        private const val DEFAULT_AA_ZOOM = 13

        /** Throttle for file-backed gesture diagnostics. */
        private const val GESTURE_LOG_INTERVAL_MS = 500L

        /** How often shared settings are re-read for live application. */
        private const val SETTINGS_RELOAD_INTERVAL_MS = 5000L
    }
}
