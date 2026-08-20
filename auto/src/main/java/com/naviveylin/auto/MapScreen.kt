@file:Suppress("DEPRECATION") // MapTemplate: no content-free map template exists in the car-app API

package com.naviveylin.auto

import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.InstalledMaps
import com.framstag.libosmscout.client.ObjectDescription
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
 * Supports destination selection: tap on map → details overlay with "Navigate here" action.
 *
 * Note: [MapTemplate] (deprecated in favor of [androidx.car.app.navigation.model.MapWithContentTemplate])
 * is used deliberately — a full-screen map without a content overlay. The replacement requires
 * a content template (List/Pane/Grid/Message) and would reserve screen space for it.
 */
@Suppress("DEPRECATION")
class MapScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()
    private val locationProvider = entryPoint.autoLocationProvider()

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
            initialViewport.zoom
        )
    }

    private var mapController: MapController? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    private var lastGestureLogMs = 0L
    private var selectionLat = Double.NaN
    private var selectionLon = Double.NaN
    private var hasSelection = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                registerSurfaceCallback()
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
                unregisterSurfaceCallback()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                mapRenderer.shutdown()
                scope.cancel()
            }
        })
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

    private fun buildTemplate(): MapTemplate {
        val builder = MapTemplate.Builder()

        // Create MapController
        mapController = MapController.Builder()
            .build()

        builder.setMapController(mapController!!)

        // MapTemplate requires exactly one of Pane or ItemList. Keep the pane
        // minimal — a single short row — so the host's bottom sheet stays thin
        // (the car-app API has no content-free map template).
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Map")
                    .build()
            )
            .build()
        builder.setPane(pane)

        // Zoom controls
        val zoomInAction = Action.Builder()
            .setTitle("+")
            .setOnClickListener(ParkedOnlyOnClickListener.create { onZoomIn() })
            .build()

        val zoomOutAction = Action.Builder()
            .setTitle("-")
            .setOnClickListener(ParkedOnlyOnClickListener.create { onZoomOut() })
            .build()

        // Menu access: push the menu screen on top of the map (the map is the
        // stack root now — popToRoot would stay on the map).
        val menuAction = Action.Builder()
            .setTitle("Menu")
            .setOnClickListener(
                ParkedOnlyOnClickListener.create {
                    screenManager.push(RootScreen(carContext, navigationViewModel))
                }
            )
            .build()

        val searchAction = Action.Builder()
            .setTitle("Search")
            .setOnClickListener(
                ParkedOnlyOnClickListener.create {
                    screenManager.push(SearchScreen(carContext, navigationViewModel))
                }
            )
            .build()

        builder.setActionStrip(
            ActionStrip.Builder()
                .addAction(menuAction)
                .addAction(searchAction)
                .addAction(zoomInAction)
                .addAction(zoomOutAction)
                .build()
        )

        return builder.build()
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
     * Shows a details overlay with "Navigate here" and "Clear" actions.
     */
    private fun onLocationSelected(lat: Double, lon: Double) {
        selectionLat = lat
        selectionLon = lon
        hasSelection = true

        // Push a details screen with the selected location
        val detailsScreen = createDetailsScreen(lat, lon)
        carContext.getCarService(ScreenManager::class.java).push(detailsScreen)
    }

    private fun createDetailsScreen(lat: Double, lon: Double): Screen {
        val client = entryPoint.autoClientProvider().client()
        val mag = mapRenderer.viewportState.value.zoom
        return object : Screen(carContext) {
            private var address: Array<String>? = null
            private var description: ObjectDescription? = null
            private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

            init {
                enableBackNavigation()
                // Reverse-geocode + describe the tapped location off the main
                // thread; invalidate when the JNI results arrive.
                loadScope.launch {
                    val result = withContext(Dispatchers.Default) {
                        var addr: Array<String>? = null
                        var desc: ObjectDescription? = null
                        try {
                            addr = client.getAddressAt(lat, lon)
                        } catch (e: Exception) {
                            Log.w(TAG, "getAddressAt failed", e)
                        }
                        try {
                            desc = client.getDescription(lat, lon, mag)
                        } catch (e: Exception) {
                            Log.w(TAG, "getDescription failed", e)
                        }
                        addr to desc
                    }
                    address = result.first
                    description = result.second
                    invalidate()
                }
            }

            override fun onGetTemplate(): PaneTemplate {
                val navigateAction = Action.Builder()
                    .setTitle("Navigate here")
                    .setOnClickListener {
                        Log.d(TAG, "Navigate to: $lat, $lon")
                        navigationViewModel.navigateTo(lat, lon)
                    }
                    .build()

                val clearAction = Action.Builder()
                    .setTitle("Clear")
                    .setOnClickListener {
                        selectionLat = Double.NaN
                        selectionLon = Double.NaN
                        hasSelection = false
                        screenManager.popToRoot()
                    }
                    .build()

                val pane = Pane.Builder()
                val rows = mutableListOf<Row>()

                // Title row: address if available, else coordinates.
                val addr = address
                val street = addr?.getOrNull(0)
                val houseNumber = addr?.getOrNull(1)
                val adminRegion = addr?.getOrNull(2)
                val postalArea = addr?.getOrNull(3)
                val addressLine = listOf(street, houseNumber)
                    .filter { !it.isNullOrBlank() }
                    .joinToString(" ")
                val title = addressLine.ifBlank { "Selected Location" }
                rows.add(
                    Row.Builder()
                        .setTitle(title)
                        .addText("${String.format("%.5f", lat)}, ${String.format("%.5f", lon)}")
                        .addAction(navigateAction)
                        .addAction(clearAction)
                        .build()
                )

                // Location context (region / postal area)
                for (part in listOf(adminRegion, postalArea)) {
                    if (!part.isNullOrBlank()) {
                        rows.add(Row.Builder().setTitle(part).build())
                    }
                }

                // Object description entries (label → value), best effort
                val desc = description
                if (desc != null) {
                    var added = 0
                    for (entry in desc.entries) {
                        val value = entry.value?.trim().orEmpty()
                        val label = entry.labelKey?.trim().orEmpty()
                        if (value.isEmpty() || label.isEmpty()) continue
                        if (added >= MAX_DESCRIPTION_ROWS) break
                        rows.add(
                            Row.Builder()
                                .setTitle(label)
                                .addText(value)
                                .build()
                        )
                        added++
                    }
                }

                rows.take(MAX_PANE_ROWS).forEach { pane.addRow(it) }

                return PaneTemplate.Builder(pane.build())
                    .setHeader(Header.Builder().setTitle("Location").setStartHeaderAction(Action.BACK).build())
                    .build()
            }
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

        /** Fallback center (Dortmund — same as the phone app default). */
        private const val DEFAULT_LAT = 51.5136
        private const val DEFAULT_LON = 7.4653

        /** City-level zoom so downloaded map data is visible immediately. */
        private const val DEFAULT_AA_ZOOM = 13

        /** Pane row caps: keep the details template compact for the host. */
        private const val MAX_PANE_ROWS = 10
        private const val MAX_DESCRIPTION_ROWS = 8

        /** Throttle for file-backed gesture diagnostics. */
        private const val GESTURE_LOG_INTERVAL_MS = 500L
    }
}
