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
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.ObjectDescription
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.details.DetailsInput
import com.naviveylin.core.details.DetailsResolver
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
 * description as labeled rows. "Navigate to" starts navigation;
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
    private var favorites: Map<String, List<FavoriteLocation>> = emptyMap()
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
        // browse map) and for the save/remove favorite action row (spec:
        // auto-destination-details — favorite management on details screen).
        loadScope.launch {
            favoritesProvider.favoriteLocations().collect { favorites ->
                this@DetailsScreen.favorites = favorites
                mapRenderer.setFavoriteLocations(favorites.values.flatten())
                invalidate()
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
        val onNavigate: () -> Unit = {
            Log.d(TAG, "Navigate to: $lat, $lon")
            navigationViewModel.navigateTo(lat, lon, destinationName())
        }

        val onShow: () -> Unit = {
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

        // "Navigate to" + "Show" as clickable rows at the top of the list:
        // ListTemplate actions are FAB-icon-only (car-app constraint) — a
        // titled action throws "exceeded max number of 0 actions with custom
        // titles", so the actions ride on rows (same as the map menu).
        val listBuilder = ItemList.Builder()
        listBuilder.addItem(buildNavigateRow(onNavigate))
        listBuilder.addItem(buildShowRow(onShow))
        // Favorite management (spec: auto-destination-details — favorite
        // management on details screen): save/remove reflects the store state.
        if (isFavorite()) {
            listBuilder.addItem(
                buildRemoveFavoriteRow {
                    loadScope.launch { favoritesProvider.removeFavorite(lat, lon) }
                }
            )
        } else {
            listBuilder.addItem(
                buildSaveFavoriteRow {
                    loadScope.launch {
                        favoritesProvider.addFavorite(resolveTitle(address, description, nameHint), lat, lon)
                    }
                }
            )
        }
        buildAttributeList(
            lat = lat,
            lon = lon,
            address = address,
            description = description,
            nameHint = nameHint
        ).forEach { listBuilder.addItem(it) }

        // All attributes as a scrollable/paged list over the map preview
        // (spec: auto-destination-details — all description attributes shown;
        // the host pages when the list exceeds one page).
        val contentTemplate = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(resolveTitle(address, description, nameHint))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()

        // Map preview with the attribute list overlaid (same pattern as the
        // map menu: MapWithContentTemplate + ListTemplate content).
        return MapTemplateFactory.buildTemplate(
            mapController = MapController.Builder().build(),
            contentTemplate = contentTemplate
        )
    }

    /**
     * Best-known destination name for the navigation context and the map
     * marker: the resolved street address, else the region, else the caller's
     * name hint (search label), else null (coordinates fallback).
     */
    private fun destinationName(): String? = resolveDestinationName(address, nameHint)

    /** True when the destination is already saved as a favorite (any group). */
    private fun isFavorite(): Boolean =
        favorites.values.flatten().any { it.lat == lat && it.lon == lon }

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
 * Builds the "Navigate to" row (spec: auto-destination-details) — the
 * first list row that starts navigation, marked with a unicode direction
 * glyph so it reads as an action. Label aligned with the phone details
 * dialog. Extracted for testability: tapping the row fires [onNavigate].
 * Rendered as a row because ListTemplate actions are FAB-icon-only (no
 * custom titles).
 */
internal fun buildNavigateRow(onNavigate: () -> Unit): Row =
    Row.Builder()
        .setTitle("\u25B6 Navigate to")
        .setOnClickListener { onNavigate() }
        .build()

/**
 * Builds the "Show" row (spec: auto-destination-details) — the second list
 * item that closes the details and shows the destination on the browse map,
 * marked with a unicode target glyph so it reads as an action. Extracted
 * for testability: tapping the row fires [onShow].
 */
internal fun buildShowRow(onShow: () -> Unit): Row =
    Row.Builder()
        .setTitle("◎ Show")
        .setOnClickListener { onShow() }
        .build()

/**
 * Builds the "Add to Favorites" row (spec: auto-destination-details —
 * favorite management on details screen), marked with a star glyph. Label
 * aligned with the phone details dialog (spec: cross-variant-ui-parity).
 * Extracted for testability: tapping the row fires [onSave].
 */
internal fun buildSaveFavoriteRow(onSave: () -> Unit): Row =
    Row.Builder()
        .setTitle("★ Add to Favorites")
        .setOnClickListener { onSave() }
        .build()

/**
 * Builds the "Remove from Favorites" row (spec: auto-destination-details —
 * favorite management on details screen), marked with an empty-star glyph.
 * Label aligned with the phone details dialog (spec: cross-variant-ui-parity).
 * Extracted for testability: tapping the row fires [onRemove].
 */
internal fun buildRemoveFavoriteRow(onRemove: () -> Unit): Row =
    Row.Builder()
        .setTitle("☆ Remove from Favorites")
        .setOnClickListener { onRemove() }
        .build()

/**
 * Builds the details attribute list (spec: auto-destination-details).
 *
 * Pure and testable: every row carries a label (title) with its value (text),
 * in order — "Coordinates" (always), "Address" (resolved street + house
 * number + postal code + city), "Area" (admin hierarchy → reverse region →
 * description IsIn → postal area), then ALL object description entries
 * (label/value) in native order — no row cap; the host pages when the list
 * exceeds one page, so every attribute returned by the description API
 * (opening hours, phone, …) is reachable. Resolution and entry filtering are
 * shared with the phone details dialog via [DetailsResolver] (phone is the
 * lead view).
 */
internal fun buildAttributeList(
    lat: Double,
    lon: Double,
    address: Array<String>?,
    description: ObjectDescription?,
    nameHint: String? = null
): List<Row> {
    val rows = mutableListOf<Row>()

    // Coordinates — always present.
    rows.add(
        Row.Builder()
            .setTitle("Coordinates")
            .addText("${String.format("%.5f", lat)}, ${String.format("%.5f", lon)}")
            .build()
    )

    // One shared resolution pass (spec: align-details-actions-and-shared-data
    // — DetailsData bundle): address, area, and the filtered display entries.
    // The caller's label doubles as the object name (POI/search results carry
    // the name in the label, phone parity) so the header shows the name even
    // when the description lacks a General/Name entry.
    val input = DetailsInput(
        label = nameHint,
        name = nameHint,
        adminRegionHierarchy = null,
        postalArea = null,
        description = description,
        resolvedAddress = address
    )
    val data = DetailsResolver.resolve(input, nameHint)
    val addressLine = data.address
    if (addressLine != null) {
        rows.add(Row.Builder().setTitle("Address").addText(addressLine).build())
    }
    val area = data.area
    if (area != null) {
        rows.add(Row.Builder().setTitle("Area").addText(area).build())
    }

    // ALL object description entries (label → value), in native order, after
    // the shared filter (blank skip + street/address dedup).
    for (entry in data.displayEntries) {
        rows.add(
            Row.Builder()
                .setTitle(entry.labelKey)
                .addText(entry.value?.trim().orEmpty())
                .build()
        )
    }

    return rows
}

/**
 * Resolves the details screen title (spec: auto-destination-details — title
 * scenarios): the object description's `General/Name` entry, else the resolved
 * full address (street + house number + postal code + city), else the caller's
 * name/label hint, else the generic "Location". Pure and testable; delegates
 * to the shared [DetailsResolver] (phone is lead view).
 */
internal fun resolveTitle(
    address: Array<String>?,
    description: ObjectDescription?,
    nameHint: String? = null
): String {
    val input = DetailsInput(
        label = nameHint,
        name = nameHint,
        adminRegionHierarchy = null,
        postalArea = null,
        description = description,
        resolvedAddress = address
    )
    return DetailsResolver.resolveTitle(input, nameHint = nameHint)
}

/**
 * Best-known destination name for the navigation context and the map marker:
 * the resolved full address, else the region, else the caller's name hint,
 * else null (coordinates fallback). Pure and testable; delegates to the
 * shared [DetailsResolver].
 */
internal fun resolveDestinationName(
    address: Array<String>?,
    nameHint: String? = null
): String? {
    val input = DetailsInput(
        label = nameHint,
        name = nameHint,
        adminRegionHierarchy = null,
        postalArea = null,
        description = null,
        resolvedAddress = address
    )
    return DetailsResolver.resolveDestinationName(input, nameHint)
}
