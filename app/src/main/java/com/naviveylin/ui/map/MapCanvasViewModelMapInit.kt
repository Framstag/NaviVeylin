package com.naviveylin.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naviveylin.R
import com.naviveylin.core.mapsource.MapTreeDiscovery
import com.naviveylin.core.NativeTileDataCache
import com.naviveylin.core.StartMapSelection
import com.naviveylin.core.TileCacheConfig
import com.naviveylin.data.ViewportState
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The off-main half of [initMap]: the asset/stylesheet refresh, the database opens, the
 * tile-data-cache configuration, the favorites-store initialization and the viewport lookup.
 *
 * Runs on [defaultDispatcher] (spec: `map-render` — Map initialization keeps native and file
 * work off the main thread; `guidelines/Design.md` §4) and publishes no state, so a cancelled
 * initialization (map re-entry) can only discard its result — never leave a half-applied one,
 * because there is nothing to half-apply.
 *
 * A superseding init cancels this coroutine while a blocking native call may already be in
 * flight (a JNI call cannot be interrupted). The [coroutineContext.ensureActive] checks therefore
 * sit between the client-mutating steps: the cancelled initialization finishes the call it was in
 * and stops before the next one, and the key it restores the viewport for is the one it was
 * started with ([mapKey], captured synchronously by [initMap]).
 *
 * @return the resolved viewport, or `null` when the selected database path was rejected (the
 *   caller publishes the error state on the main thread).
 */
internal suspend fun MapCanvasViewModel.initializeOffMain(mapPath: String, mapKey: String): MapInitResult? {
    val stylesheetsDir = assetCopier.ensureStylesheets()
    Log.d(MapCanvasViewModel.TAG, "initMap: stylesheets=$stylesheetsDir")

    Log.d(MapCanvasViewModel.TAG, "initMap: opening database...")
    val opened = try {
        client.openDatabase(mapPath).also { success ->
            Log.d(MapCanvasViewModel.TAG, "initMap: openDatabase returned $success")
        }
    } catch (e: Exception) {
        Log.e(MapCanvasViewModel.TAG, "Failed to open database", e)
        false
    }
    if (!opened) return null

    Log.d(MapCanvasViewModel.TAG, "initMap: database opened successfully")
    coroutineContext.ensureActive()

    // Configure the native tile data cache capacity (regional database
    // and any future basemap) through the shared seam (spec:
    // native-tile-data-cache). Stored in the client and re-applied to
    // every open database by the render path, so it also covers
    // databases that open asynchronously after this call. Perf-only and
    // idempotent; the highest requested value wins and is never lowered, so
    // a car session sharing this client cannot shrink it and a car-first
    // ordering cannot degrade the phone.
    when (NativeTileDataCache.apply(client, NativeTileDataCache.PHONE_TILES)) {
        TileCacheConfig.APPLIED ->
            Log.d(MapCanvasViewModel.TAG, "initMap: tile data cache configured with " +
                    "${NativeTileDataCache.PHONE_TILES} tiles")
        TileCacheConfig.UNCHANGED -> Unit
        TileCacheConfig.REJECTED ->
            Log.w(MapCanvasViewModel.TAG, "initMap: tile data cache kept at the higher value already configured")
        TileCacheConfig.FAILED ->
            Log.w(MapCanvasViewModel.TAG, "initMap: setNativeDataCacheSize failed")
    }
    coroutineContext.ensureActive()

    // Open all other installed maps too, so every downloaded region
    // renders via viewport coverage — no switching needed (multi-map).
    try {
        val mapsDir = File(context.filesDir, "maps")
        // Shared with Android Auto (AutoServiceModule): every database
        // directory under maps/ (any depth), except the basemap overlay and
        // the staging directories an interrupted update left behind — those
        // are reported, never opened as a map (spec: map-updates).
        val installed = MapTreeDiscovery.under(
            mapsDir.toPath(),
            mapsDir.toPath().resolve("basemap")
        ).installed.map { it.toString() }
        // Record the database this screen opened, so the next start reopens it (spec:
        // start-map-selection — the last opened map is reopened at the next start; a map opened
        // without the map manager is recorded). The value is normalized to the database directory
        // the open path refers to, so a container path (an archive with its own top-level
        // directory) still matches the discovery's candidates. Runs in this off-main section and
        // through the serialized settings transaction, so a concurrent car write is not lost
        // (spec: settings-persistence).
        settingsStorage.update {
            it.copy(lastMapPath = StartMapSelection.databaseDirectoryFor(installed, mapPath))
        }
        coroutineContext.ensureActive()
        val additional = installed.filter { it != mapPath }
        if (additional.isNotEmpty()) {
            // One batch call: each openDatabase() call closes and reopens
            // every open database on the database thread, so a
            // per-directory loop costs one database-set change per
            // directory for a single logical set (spec:
            // native-database-open — A whole database set is registered in
            // one coordinated change).
            val registered = client.openDatabases(additional.toTypedArray())
            val notRegistered = additional.filterIndexed { index, _ ->
                !registered.getOrElse(index) { false }
            }
            if (notRegistered.isNotEmpty()) {
                Log.w(MapCanvasViewModel.TAG, "initMap: not registered: $notRegistered")
            }
        }
    } catch (e: Exception) {
        Log.w(MapCanvasViewModel.TAG, "initMap: failed to open additional maps", e)
    }
    coroutineContext.ensureActive()

    // Initialise favorites repository
    val favPath = context.filesDir.resolve(MapCanvasViewModel.FAVORITES_FILE).absolutePath
    favoriteRepository.init(favPath)
    coroutineContext.ensureActive()

    // Load persisted viewport for this map; fall back to the map's bounding box center, then to
    // the global default. Resolved HERE, before the caller creates a renderer, so an early
    // setScreenSize/renderMap cannot submit a render with the uninitialized default viewport
    // (which would clobber the restore).
    val saved = viewportStorage.load(mapKey)
    val bbox = try {
        client.getDatabaseBoundingBox(mapPath)
    } catch (e: Exception) {
        Log.w(MapCanvasViewModel.TAG, "initMap: bounding box lookup failed", e)
        null
    }
    val default = ViewportState()
    val restored = saved ?: if (bbox != null && bbox.size >= 4) {
        ViewportState(
            centerLat = (bbox[0] + bbox[2]) / 2.0,
            centerLon = (bbox[1] + bbox[3]) / 2.0,
            magnification = default.magnification
        )
    } else {
        default
    }
    // A persisted world-zoom viewport (mag < 4) renders the whole globe in
    // native and can hang the render worker — clamp the restore to the same
    // floor the gesture/zoom controls enforce (specs: min magnification 4).
    val vp = restored.copy(magnification = restored.magnification.coerceIn(MapCanvasViewModel.MIN_MAG, MapCanvasViewModel.MAX_MAG))
    return MapInitResult(
        viewport = vp,
        fromSavedViewport = saved != null,
        fromBoundingBox = bbox != null
    )
}

/**
 * Result of [initializeOffMain]: the restored viewport plus which fallback produced it, for the
 * one log line that names it.
 */
internal class MapInitResult(
    val viewport: ViewportState,
    val fromSavedViewport: Boolean,
    val fromBoundingBox: Boolean
)

/** Initialise with a map database path. Call once from the screen. */
fun MapCanvasViewModel.initMap(mapPath: String) {
    Log.d(MapCanvasViewModel.TAG, "initMap: initialising with path=$mapPath")
    currentMapKey = mapPath.substringAfterLast('/')
    // Captured synchronously: the off-main section (initializeOffMain) restores the viewport for
    // THIS initialization's map even if a re-entry overwrites currentMapKey before it runs.
    val mapKey = currentMapKey ?: mapPath
    // Re-arm the save guard: until the persisted viewport restore is
    // applied below, a lifecycle save must not write the default viewport.
    viewportRestored = false

    // Supersede any in-flight init: its coroutine may be suspended (e.g.
    // favoriteRepository.init runs withContext(defaultDispatcher)), and if
    // it resumed after the re-entry it would apply a stale restore, re-arm
    // the save guard, and overwrite mapRenderer with a second renderer.
    // Cancellation aborts it at its first suspension point.
    initJob?.cancel()

    // Tear down any previous renderer so a re-entry (MAIN screen after
    // map downloads) initialises cleanly instead of stacking renderers.
    rendererScope?.cancel()
    mapRenderer?.shutdown()
    mapRenderer = null

    initJob = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // The loading state is published first, on the main thread, so it covers the whole
        // off-main section below (spec: map-render — the loading state is observable before
        // the work starts). `density` is a plain resource read and stays here with the
        // renderer that consumes it.
        val density = context.resources.displayMetrics.densityDpi.toDouble()

        // Every native and filesystem call of initialization runs off the main thread
        // (`Design.md` §4 first rule; spec: map-render — Map initialization keeps native and
        // file work off the main thread). The section publishes no state, so a cancelled init
        // (map re-entry) can only discard its result, never leave a half-applied one behind.
        val init = withContext(defaultDispatcher) { initializeOffMain(mapPath, mapKey) }

        if (init == null) {
            Log.e(MapCanvasViewModel.TAG, "initMap: failed to open database at $mapPath")
            mapReady.value = true
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = context.getString(R.string.map_open_failed)
            )
            return@launch
        }

        // The restored viewport is already resolved. Ordering below is load-bearing: the
        // renderer must not exist while the restore is applied (initializeOffMain resolves it
        // first), and the restored viewport is published before the render frame collector is
        // wired, so an early render cannot overwrite the restored center on disk.
        val vp = init.viewport
        Log.d(
            MapCanvasViewModel.TAG,
            "initMap: viewport mag=${vp.magnification} " +
                (if (init.fromSavedViewport) "(saved)" else if (init.fromBoundingBox) "(bbox)" else "(default)")
        )

        // Create MapRenderer on a dedicated background scope so heavy JNI renders
        // never block the main thread and the UI stays responsive. The scope takes the injectable
        // renderer dispatcher (not an inline Dispatchers.Default), so a test can run the renderer's
        // work — including its debounce — on its own scheduler.
        val scope = CoroutineScope(SupervisorJob() + rendererDispatcher)
        rendererScope = scope
        val renderer = MapRenderer(client, density, scope)
        renderer.renderMode = _uiState.value.renderMode
        // Re-apply the last known screen size: onSizeChanged only fires on size
        // CHANGE, so re-entering this screen (same size) would leave the new
        // renderer at 0x0 and every render would be skipped.
        renderer.screenWidth = screenWidth
        renderer.screenHeight = screenHeight
        mapRenderer = renderer

        // Sync the renderer to the restored viewport BEFORE any init-time
        // submit (dark/style pushes, favorites): those render at the
        // renderer's current viewport, which is DEFAULT (mag 5) until the
        // first state-driven renderMap. On re-entry there is no size-change
        // renderMap, so the first frame would show a zoomed-out map until a
        // manual refresh (observed after basemap reload + switch back).
        renderer.prepareViewport(vp.centerLat, vp.centerLon, vp.magnification, vp.angle)

        // Apply the restored viewport BEFORE wiring the view-change listener:
        // the listener persists every completed render, and an early render
        // with the uninitialized default viewport would overwrite the restored
        // center on disk (observed: viewport-<map>.json with center dropped).
        _uiState.value = _uiState.value.copy(viewport = vp, isLoading = false)
        // The restore is now visible to saveViewport — lifecycle saves may
        // persist from here on.
        viewportRestored = true

        // Wire frame emissions to UI state. Each frame carries bitmap + viewport +
        // marker snapshot as ONE atomic emission, so the overlay never sees state
        // from different frames (which would make the marker jump off the road).
        viewModelScope.launch {
            renderer.frameFlow.collect { frame -> applyRenderedFrame(frame) }
        }

        // Wire view change listener for viewport persistence (per map)
        renderer.addViewChangeListener(object : MapRenderer.ViewChangeListener {
            override fun onViewChanged(lat: Double, lon: Double, mag: Double, angle: Double) {
                viewModelScope.launch {
                    // A walked step is never persisted (spec: smooth-zoom - Viewport
                    // records final magnification): while a walk is pending, persist
                    // the magnification it is heading for.
                    viewportStorage.save(
                        currentMapKey ?: mapPath,
                        ViewportState(lat, lon, zoomWalk.persistMag(mag))
                    )
                }
            }
        })

        // Set initial favorites on renderer
        val favs = favoriteRepository.favorites.value
        val allFavs = mutableListOf<com.framstag.libosmscout.client.FavoriteLocation>()
        for ((_, list) in favs) {
            allFavs.addAll(list)
        }
        if (allFavs.isNotEmpty()) {
            renderer.setFavoriteLocations(allFavs.toTypedArray())
        }

        // Apply the resolved dark presentation and the persisted map style, in the order the
        // native side needs: the flag push happens after the database is open (`SetStyleFlag` is
        // a no-op before that), and the stylesheet load carries the flag set, so the first render
        // already uses the right variant and style (spec: map-styles - Persisted style applied on
        // start; `guidelines/MapRendering.md` section 15).
        ensureMapStyle(
            _uiState.value.styleSheet,
            resolvedDaylight(),
            MapStyleApplyReason.STARTUP_DB_READY,
            allowDefaultFallback = true
        )
        // The push above happens after the database is open, so SetStyleFlag
        // is effective — mark it as done so the first front-buffer frame does
        // not re-push and invalidate the freshly rendered tiles.
        stylePushedToNative = true

        // The preference is native client state, not part of the stylesheet load, so it is pushed
        // before the first render and the first frame already follows it (spec: `map-styles` —
        // Preference is applied at start and on change).
        applyPreferSymbolIcons(_uiState.value.preferSymbolPoiIcons, invalidate = false)

        // The style value comes from the settings load that ran at ViewModel init; its load is
        // part of the single `ensureMapStyle` call above.

        Log.d(MapCanvasViewModel.TAG, "initMap: triggering first render")
        if (phoneMapRenderRefused) {
            Log.d(MapCanvasViewModel.TAG, "initMap: first render refused — phone map suspended by a car session")
        } else {
            renderer.requestRender(vp.centerLat, vp.centerLon, vp.magnification)
        }
        mapReady.value = true
    }
}
