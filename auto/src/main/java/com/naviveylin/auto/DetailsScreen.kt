package com.naviveylin.auto

import android.util.Log
import android.view.View
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.ProjectionUtils
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Destination details screen for Android Auto (spec: auto-destination-details).
 *
 * Shows a map preview of the destination (libosmscout-rendered via
 * [AutoMapRenderer], destination marker at the position) with a pane overlaid
 * containing the reverse-geocoded address, coordinates, and object
 * description as labeled rows. "Navigate here" (primary) starts navigation;
 * "Show" closes the details and centers the browse map on the destination.
 * Shared by all entry points: map tap, search results, and POI results.
 *
 * When [preloadedDescription] is provided (single-candidate map tap), the
 * object description is not re-queried.
 */
class DetailsScreen(
    carContext: CarContext,
    private val navigationViewModel: NavigationViewModel,
    private val lat: Double,
    private val lon: Double,
    private val preloadedDescription: ObjectDescription? = null,
    private val mag: Int = DEFAULT_MAGNIFICATION,
    private val nameHint: String? = null
) : Screen(carContext) {

    private var address: Array<String>? = null
    private var description: ObjectDescription? = null
    private var gpsPosition: AutoPosition? = null
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val locationProvider = entryPoint.autoLocationProvider()
    private val favoritesProvider = entryPoint.autoFavoritesProvider()

    /** Static map preview centered on the destination (no gestures). */
    private val mapRenderer: AutoMapRenderer by lazy {
        val client = entryPoint.autoClientProvider().client()
        // The native renderer projects with the client's configured physical
        // DPI (from the phone display metrics), not the car surface DPI — all
        // overlay math must use the same value (same as MapScreen).
        val renderDpi = carContext.resources.displayMetrics.densityDpi.toDouble()
        AutoMapRenderer(client, renderDpi, lat, lon, mag)
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        enableBackNavigation()
        val client = entryPoint.autoClientProvider().client()
        // Reverse-geocode + describe the selected location off the main
        // thread; invalidate when the JNI results arrive. When the caller
        // already resolved the object (single candidate), the description is
        // passed in and not re-queried.
        loadScope.launch {
            val result = withContext(Dispatchers.Default) {
                var addr: Array<String>? = null
                var desc: ObjectDescription? = preloadedDescription
                try {
                    addr = client.getAddressAt(lat, lon)
                } catch (e: Exception) {
                    Log.w(TAG, "getAddressAt failed", e)
                }
                if (desc == null) {
                    try {
                        desc = client.getDescription(lat, lon, mag)
                    } catch (e: Exception) {
                        Log.w(TAG, "getDescription failed", e)
                    }
                }
                addr to desc
            }
            address = result.first
            description = result.second
            // Refresh the destination marker label once the name is known.
            mapRenderer.setDestinationMarker(lat, lon, destinationName())
            invalidate()
        }

        // Observe favorites for markers on the preview (parity with the
        // browse map).
        loadScope.launch {
            favoritesProvider.favoriteLocations().collect { favorites ->
                mapRenderer.setFavoriteLocations(favorites.values.flatten())
            }
        }

        // Observe the current position: draw the GPS marker on the preview
        // and, when both the destination and a fix are known, zoom so both
        // are visible (parity with the phone mini map, spec:
        // auto-destination-details — map preview).
        loadScope.launch {
            locationProvider.position().collect { pos ->
                gpsPosition = pos
                if (pos != null) {
                    mapRenderer.setGpsMarker(pos.lat, pos.lon, pos.bearing, pos.accuracy)
                    applyViewport()
                }
            }
        }

        // If the host delivered a surface we cannot lock (AAOS emulator
        // quirk), drop it and ask the host for a fresh one. Throttled inside
        // the renderer and capped per screen start (same as MapScreen).
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
            }
            override fun onStop(owner: LifecycleOwner) {
                mapRenderer.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start (same as
                // MapScreen).
                mapRenderer.releaseSurface()
            }
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterSurfaceCallback()
                mapRenderer.shutdown()
                loadScope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val navigateAction = buildNavigateAction {
            Log.d(TAG, "Navigate to: $lat, $lon")
            navigationViewModel.navigateTo(lat, lon, destinationName())
        }

        val showAction = buildShowAction {
            Log.d(TAG, "Show on map: $lat, $lon")
            val screenManager = carContext.getCarService(ScreenManager::class.java)
            screenManager.popToRoot()
            screenManager.push(
                MapScreen(
                    carContext, navigationViewModel,
                    initialCenter = lat to lon,
                    initialZoom = mag,
                    initialDestinationName = destinationName()
                )
            )
        }

        val rows = buildDetailsRows(
            lat = lat,
            lon = lon,
            address = address,
            description = description,
            nameHint = nameHint
        )

        val pane = buildDetailsPane(
            rows = rows,
            navigateAction = navigateAction,
            showAction = showAction
        )

        // Map preview with the pane overlaid (spec: auto-destination-details —
        // map preview). The pane header carries the destination-derived title.
        return MapTemplateFactory.buildTemplate(
            mapController = MapController.Builder().build(),
            contentTemplate = PaneTemplate.Builder(pane)
                .setHeader(
                    Header.Builder()
                        .setTitle(resolveTitle(address, description, nameHint))
                        .setStartHeaderAction(Action.BACK)
                        .build()
                )
                .build()
        )
    }

    /**
     * Best-known destination name for the navigation context and the map
     * marker: the resolved street address, else the region, else the caller's
     * name hint (search label), else null (coordinates fallback).
     */
    private fun destinationName(): String? = resolveDestinationName(address, nameHint)

    private fun registerSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(object : SurfaceCallback {
            override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                val surface = surfaceContainer.surface ?: return
                surfaceWidth = surfaceContainer.width
                surfaceHeight = surfaceContainer.height
                surfaceDpi = surfaceContainer.dpi.takeIf { it > 0 }?.toDouble() ?: DEFAULT_DPI
                Log.d(TAG, "Details surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
                // Render at the CAR display's DPI (same as MapScreen).
                runCatching { entryPoint.autoClientProvider().client().setMapDpi(surfaceDpi) }
                    .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                mapRenderer.updateProjectionDpi(surfaceDpi)
                mapRenderer.setDestinationMarker(lat, lon, destinationName())
                mapRenderer.onSurfaceCreated(surface, surfaceWidth, surfaceHeight)
                // Center the destination in the visible map area (the part
                // not covered by the host's pane panel) once the surface
                // dimensions are known.
                applyViewport()
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Details surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                mapRenderer.onSurfaceDestroyed()
            }

            // Static preview: no gesture handling (design decision 2).
            override fun onScroll(distanceX: Float, distanceY: Float) = Unit
            override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = Unit
            override fun onClick(x: Float, y: Float) = Unit
        })
    }

    private fun unregisterSurfaceCallback() {
        val appManager = carContext.getCarService(AppManager::class.java)
        appManager.setSurfaceCallback(null)
    }

    /**
     * Center the viewport so the destination (or the destination-to-fix
     * midpoint when a GPS fix is known) sits in the center of the visible map
     * area — the part not covered by the host's pane panel. Without a fix the
     * preview stays at [mag]; with a fix it zooms so both points are visible.
     */
    private fun applyViewport() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        val pos = gpsPosition
        val rtl = carContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        if (pos != null) {
            val zoom = fitZoom(lat, lon, pos.lat, pos.lon)
            val midLat = (lat + pos.lat) / 2.0
            val midLon = (lon + pos.lon) / 2.0
            val (clat, clon) = paneOffsetCenter(midLat, midLon, zoom, surfaceWidth, surfaceHeight, surfaceDpi, rtl)
            mapRenderer.setViewport(clat, clon, zoom, 0.0)
        } else {
            val (clat, clon) = paneOffsetCenter(lat, lon, mag, surfaceWidth, surfaceHeight, surfaceDpi, rtl)
            mapRenderer.setViewport(clat, clon, mag, 0.0)
        }
    }

    /**
     * Largest whole zoom level whose ground span (smaller viewport dimension,
     * Mercator at the destination latitude) covers the destination-to-fix
     * distance with padding. Clamped to the renderer's zoom range.
     */
    private fun fitZoom(destLat: Double, destLon: Double, posLat: Double, posLon: Double): Int {
        val dLat = Math.toRadians(posLat - destLat)
        val dLon = Math.toRadians(posLon - destLon)
        val a = sin(dLat / 2.0).pow(2) +
            cos(Math.toRadians(destLat)) * cos(Math.toRadians(posLat)) * sin(dLon / 2.0).pow(2)
        val distance = 2.0 * ProjectionUtils.EARTH_RADIUS * asin(sqrt(a))
        if (distance <= 0.0) return mag
        val minDim = min(surfaceWidth, surfaceHeight).coerceAtLeast(1)
        val extentMeter = 2.0 * Math.PI * ProjectionUtils.EARTH_RADIUS
        // Ground span at zoom z for minDim pixels:
        //   span = minDim * (extentMeter / 2^z / 256) * (REFERENCE_DPI / dpi)
        // Require span >= distance * margin (both points + padding).
        val margin = 1.6
        val z = log2(
            extentMeter * minDim * ProjectionUtils.REFERENCE_DPI /
                (256.0 * surfaceDpi * distance * margin)
        )
        return z.toInt().coerceIn(AutoMapRenderer.MIN_ZOOM, AutoMapRenderer.MAX_ZOOM)
    }

    companion object {
        private const val TAG = "DetailsScreen"

        /** Magnification for object description queries when no map viewport is available. */
        const val DEFAULT_MAGNIFICATION = 15

        private const val DEFAULT_DPI = 160.0

        /** Max invalidate() calls to recover a dead surface per screen start. */
        private const val MAX_SURFACE_REFRESH_ATTEMPTS = 2
    }
}

/**
 * Builds the "Navigate here" action (spec: auto-destination-details) — the
 * pane-level primary button that starts navigation. Extracted for
 * testability: invoking the action fires [onNavigate].
 */
internal fun buildNavigateAction(onNavigate: () -> Unit): Action =
    Action.Builder()
        .setTitle("Navigate here")
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { onNavigate() }
        .build()

/**
 * Builds the "Show" action (spec: auto-destination-details) — the pane-level
 * secondary button that closes the details and shows the destination on the
 * browse map. Extracted for testability: invoking the action fires [onShow].
 */
internal fun buildShowAction(onShow: () -> Unit): Action =
    Action.Builder()
        .setTitle("Show")
        .setOnClickListener { onShow() }
        .build()

private const val MAX_PANE_ROWS = 4

/**
 * Builds the details pane rows (spec: auto-destination-details).
 *
 * Pure and testable: every row carries a label (title) with its value (text),
 * in priority order — "Coordinates" (always), "Address" (street + house
 * number), "Area" (admin region, else postal area), then object description
 * entries (label/value) filling the remaining slots up to the pane cap.
 * PaneTemplate rows are non-actionable — actions live at pane level
 * (see [buildDetailsPane]).
 */
internal fun buildDetailsRows(
    lat: Double,
    lon: Double,
    address: Array<String>?,
    description: ObjectDescription?,
    nameHint: String? = null
): List<Row> {
    val rows = mutableListOf<Row>()

    val street = address?.getOrNull(0)
    val houseNumber = address?.getOrNull(1)
    val adminRegion = address?.getOrNull(2)
    val postalArea = address?.getOrNull(3)

    // Coordinates — always present.
    rows.add(
        Row.Builder()
            .setTitle("Coordinates")
            .addText("${String.format("%.5f", lat)}, ${String.format("%.5f", lon)}")
            .build()
    )

    // Address — street + house number when either is present.
    val addressLine = listOf(street, houseNumber)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
    if (addressLine.isNotBlank()) {
        rows.add(Row.Builder().setTitle("Address").addText(addressLine).build())
    }

    // Area — admin region, else postal area.
    val area = adminRegion?.takeIf { it.isNotBlank() }
        ?: postalArea?.takeIf { it.isNotBlank() }
    if (area != null) {
        rows.add(Row.Builder().setTitle("Area").addText(area).build())
    }

    // Object description entries (label → value), best effort, filling the
    // remaining slots up to the pane cap.
    if (description != null) {
        for (entry in description.entries) {
            if (rows.size >= MAX_PANE_ROWS) break
            val value = entry.value?.trim().orEmpty()
            val label = entry.labelKey?.trim().orEmpty()
            if (value.isEmpty() || label.isEmpty()) continue
            rows.add(
                Row.Builder()
                    .setTitle(label)
                    .addText(value)
                    .build()
            )
        }
    }

    return rows
}

/**
 * Builds the details pane (spec: auto-destination-details).
 *
 * PaneTemplate rows are NOT actionable on car hosts — action buttons must be
 * added at pane level. "Navigate here" is the primary button; "Show" is the
 * secondary button. The pane shows at most [MAX_PANE_ROWS] rows; the host
 * ignores extra rows.
 */
internal fun buildDetailsPane(
    rows: List<Row>,
    navigateAction: Action,
    showAction: Action
): Pane {
    val builder = Pane.Builder()
    rows.take(MAX_PANE_ROWS).forEach { builder.addRow(it) }
    builder.addAction(navigateAction)
    builder.addAction(showAction)
    return builder.build()
}

/**
 * Resolves the details screen title (spec: auto-destination-details — title
 * scenarios): the object description's `General/Name` entry, else the resolved
 * address (street + house number, else admin region), else the caller's name
 * hint, else the generic "Location". Pure and testable.
 */
internal fun resolveTitle(
    address: Array<String>?,
    description: ObjectDescription?,
    nameHint: String? = null
): String {
    val name = description?.entries?.firstOrNull {
        it.sectionKey == "General" && it.labelKey == "Name"
    }?.value?.takeIf { it.isNotBlank() }
    if (name != null) return name
    val street = address?.getOrNull(0)
    val houseNumber = address?.getOrNull(1)
    val addressLine = listOf(street, houseNumber)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
    if (addressLine.isNotBlank()) return addressLine
    val adminRegion = address?.getOrNull(2)
    if (!adminRegion.isNullOrBlank()) return adminRegion
    return nameHint?.takeIf { it.isNotBlank() } ?: "Location"
}

/**
 * Best-known destination name for the navigation context and the map marker:
 * the resolved street address, else the region, else the caller's name hint,
 * else null (coordinates fallback). Pure and testable.
 */
internal fun resolveDestinationName(
    address: Array<String>?,
    nameHint: String? = null
): String? {
    val street = address?.getOrNull(0)
    val houseNumber = address?.getOrNull(1)
    val addressLine = listOf(street, houseNumber)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
    if (addressLine.isNotBlank()) return addressLine
    val adminRegion = address?.getOrNull(2)
    if (!adminRegion.isNullOrBlank()) return adminRegion
    return nameHint?.takeIf { it.isNotBlank() }
}
