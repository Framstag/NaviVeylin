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
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.DiagnosticsLog
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Android Auto screen that displays a libosmscout-rendered map via [MapWithContentTemplate].
 *
 * Uses [SurfaceCallback] (registered via [AppManager]) to receive a [android.view.Surface]
 * for [AutoMapRenderer] to draw on. Gesture handling (pan, zoom, click) is done through
 * [SurfaceCallback] methods.
 *
 * Supports destination selection: tap on map → details overlay with "Navigate here" action.
 */
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

    private val mapRenderer: AutoMapRenderer by lazy {
        val clientProvider = entryPoint.autoClientProvider()
        AutoMapRenderer(clientProvider.client())
    }

    private var mapController: MapController? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
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
            buildTemplate()
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "MapWithContentTemplate build failed", e)
            SafeScreen.errorTemplate(e.message)
        }
    }

    private fun buildTemplate(): MapWithContentTemplate {
        val builder = MapWithContentTemplate.Builder()

        // Create MapController
        mapController = MapController.Builder()
            .build()

        builder.setMapController(mapController!!)

        // Zoom controls
        val zoomInAction = Action.Builder()
            .setTitle("+")
            .setOnClickListener(ParkedOnlyOnClickListener.create { onZoomIn() })
            .build()

        val zoomOutAction = Action.Builder()
            .setTitle("-")
            .setOnClickListener(ParkedOnlyOnClickListener.create { onZoomOut() })
            .build()

        val reCenterAction = Action.Builder()
            .setTitle("Re-center")
            .setOnClickListener(ParkedOnlyOnClickListener.create { onReCenter() })
            .build()

        builder.setActionStrip(
            ActionStrip.Builder()
                .addAction(zoomInAction)
                .addAction(zoomOutAction)
                .addAction(reCenterAction)
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
                // Convert scroll to viewport change. Positive deltas move the map
                // with the finger: drag right → center west, drag down → center north.
                val vp = mapRenderer.viewportState.value
                val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenter(
                    distanceX.toDouble(), distanceY.toDouble(),
                    vp.zoom,
                    surfaceWidth.toDouble(), surfaceHeight.toDouble(),
                    vp.lat, vp.lon,
                    surfaceDpi
                )
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
                    surfaceDpi
                )
                mapRenderer.setViewport(newLat, newLon, newZoom, vp.angle, newFraction)
            }

            override fun onClick(x: Float, y: Float) {
                // Convert screen coordinates to geo coordinates
                val vp = mapRenderer.viewportState.value
                val (lat, lon) = ProjectionUtils.screenToGeo(
                    x.toDouble(), y.toDouble(),
                    surfaceWidth, surfaceHeight,
                    vp.zoom, vp.lat, vp.lon,
                    surfaceDpi
                )
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
        return object : Screen(carContext) {
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
                    .addRow(
                        Row.Builder()
                            .setTitle("Selected Location")
                            .addText("${"%.5f".format(lat)}, ${"%.5f".format(lon)}")
                            .addAction(navigateAction)
                            .addAction(clearAction)
                            .build()
                    )
                    .build()

                return PaneTemplate.Builder(pane)
                    .setTitle("Location")
                    .build()
            }
        }
    }

    private fun startObserving() {
        if (observeJob != null) return
        observeJob = scope.launch {
            // Observe GPS position for follow mode and marker
            navigationViewModel.state
                .map { it.position }
                .distinctUntilChanged()
                .collect { position ->
                    if (position != null) {
                        mapRenderer.setGpsMarker(
                            position.lat, position.lon,
                            position.bearing, position.accuracy
                        )
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

    private fun onReCenter() {
        mapRenderer.reCenter()
    }

    companion object {
        private const val TAG = "MapScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
        private const val SURFACE_WIDTH = 1920
        private const val SURFACE_HEIGHT = 1080
        private const val DEFAULT_DPI = 160.0
        private const val SCALE_JITTER_THRESHOLD = 0.02f
    }
}
