@file:Suppress("DEPRECATION") // MapTemplate: no content-free map template exists in the car-app API

package com.naviveylin.auto

import android.graphics.Rect
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
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.AutoSettings
import com.naviveylin.core.NavigationViewModel
import com.naviveylin.core.ProjectionUtils
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.VehicleAnchorPosition
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val initialDestinationName: String? = null,
    /** Resolved dark presentation (preference × host signal; see [NavigationSession.resolvedDark]). */
    private val resolvedDark: StateFlow<Boolean> = MutableStateFlow(carContext.isDarkMode()),
    /** Notifies the session that the shared dark mode preference changed (PreferencesScreen saves). */
    private val onDarkModeChanged: (String) -> Unit = {}
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null
    private var rendererInitJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()

    /** Applies the shared map style to the native client (deduped). */
    private val styleApplier = CarStyleApplier(
        onLoadFailed = { style ->
            // A rejected stylesheet keeps the previous style active and is
            // reported through the shared seam + the car notice (spec:
            // map-styles — "Car reports the failure").
            Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
            reportCarStyleLoadFailure(
                appContext = carContext.applicationContext,
                client = entryPoint.autoClientProvider().client(),
                requestedStyle = style
            )?.let { styleLoadNotifier.notify(it) }
        },
        loadStyle = { style -> entryPoint.autoClientProvider().client().loadStyleSheet(style) }
    )

    /** Posts the car-side stylesheet-failure notice (non-blocking; no template change). */
    private val styleLoadNotifier = CarStyleLoadNotifier(carContext.applicationContext)

    /** Applies the host day/night state to the stylesheet `daylight` flag (deduped). */
    private val daylightApplier = CarDaylightApplier { dark ->
        try {
            entryPoint.autoClientProvider().client().setStyleSheetFlag("daylight", !dark)
            true
        } catch (e: Exception) {
            Log.w(TAG, "setStyleSheetFlag failed", e)
            false
        }
    }

    /**
     * Renderer-bound state buffers here until the renderer is ready (spec:
     * auto-map-renderer — "Renderer initialization off the car-app main
     * thread"): native client first-touch and the initial-viewport resolution
     * run in the [rendererInitJob] coroutine on a background dispatcher, so
     * the constructor never blocks the main thread and never first-touches the
     * Hilt native-client singleton on the host thread.
     */
    private val rendererGate = RendererGate()

    /** Throttled street-name resolution (same pattern as free driving). */
    private val streetNameUpdater = StreetNameUpdater()
    private var streetJob: Job? = null

    /** Current street name label text; null/blank = nothing drawn. */
    @Volatile
    private var streetName: String? = null

    /**
     * Browse follow anchor from the shared free-driving setting (design D7,
     * spec: auto/browse — the browse view SHALL use the shared free-driving
     * anchor setting for the vehicle anchor row). Defaults to center.
     */
    @Volatile
    private var browseAnchor: VehicleAnchorPosition = VehicleAnchorPosition.DEFAULT

    private var mapController: MapController? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceDpi = DEFAULT_DPI
    private var lastGestureLogMs = 0L
    private var lastSettingsReloadMs = 0L

    /** Host guaranteed-visible area (design D8: street pill band insets). */
    private val stableArea = Rect()

    /** Host currently-visible area (design D8: pill top edge — real coverage). */
    private val visibleArea = Rect()

    /** Surface-refresh (invalidate) attempts left for this screen start. */
    private var surfaceRefreshAttempts = 0

    init {
        // Browse-mode overlay: the street-name pill only (design D5/D8, spec:
        // auto/browse). Draw-only, no interactive elements — the
        // surface-gesture constraint of auto-map-layout is untouched. The
        // pill anchors to the edge of the guaranteed-visible band (surface -
        // host chrome insets from the stable area) opposite the browse anchor
        // row (row rule shared with free driving).
        rendererGate.setOverlayDrawer({ canvas, w, h ->
            val density = (surfaceDpi / 160.0).toFloat()
            val (topInset, bottomInset) = hostInsetsPx()
            StreetNameLabel.draw(
                canvas = canvas,
                surfaceWidth = w,
                surfaceHeight = h,
                density = density,
                name = streetName.orEmpty(),
                placement = StreetNameLabel.placementFor(browseAnchor),
                topInset = topInset,
                bottomInset = bottomInset
            )
        })

        // Renderer readiness: wire surface-failure recovery and re-push the
        // day/night stylesheet flag once the renderer exists (the pre-ready
        // push is skipped — pushDark must never first-touch the native client
        // on the main thread).
        scope.launch {
            rendererGate.renderer.collect { renderer ->
                if (renderer == null) return@collect
                // If the host delivered a surface we cannot lock (AAOS emulator
                // quirk: it locks surfaces it re-delivers after a screen
                // transition), drop it and ask the host for a fresh one.
                // Throttled inside the renderer and capped per screen start so a
                // persistently-bad surface does not invalidate the template
                // forever.
                renderer.onSurfaceFailed = {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                        surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
                    ) {
                        surfaceRefreshAttempts++
                        Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                        invalidate()
                    }
                }
                // The startup push was skipped while the client was still
                // building — apply the resolved presentation now.
                pushDark()
            }
        }

        // Async renderer init (design D2): first-touch the native client
        // singleton off the main thread, resolve the initial viewport (saved
        // phone viewport JSON / map bbox — file IO + native bounding-box
        // queries), build the renderer and publish it into the gate. Until
        // then the gate buffers surface/dark/viewport/marker state; the
        // constructor stays on the main thread only for cheap bookkeeping.
        rendererInitJob = scope.launch {
            val renderer = withContext(Dispatchers.Default) {
                val client = entryPoint.autoClientProvider().client()
                rendererGate.pendingSurfaceDpi()?.let { dpi ->
                    runCatching { client.setMapDpi(dpi) }
                        .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                }
                val viewport = resolveInitialAutoViewport(
                    mapsRootDir = File(carContext.filesDir, "maps"),
                    client = client,
                    initialCenter = initialCenter,
                    initialZoom = initialZoom,
                    defaultZoom = DEFAULT_AA_ZOOM,
                    defaultCenter = DEFAULT_LAT to DEFAULT_LON
                )
                Log.d(TAG, "AA renderer ready at ${viewport.lat},${viewport.lon} mag=${viewport.zoom}")
                // The native renderer projects with the client's configured
                // physical DPI (from the phone display metrics), not the car
                // surface DPI — all overlay math (gestures, GPS marker) must
                // use the same value.
                AutoMapRenderer(
                    client,
                    carContext.resources.displayMetrics.densityDpi.toDouble(),
                    viewport.lat,
                    viewport.lon,
                    viewport.zoom,
                    // "Show location" maps must stay on the requested
                    // destination — follow mode would snap the viewport to
                    // every GPS fix.
                    initialFollowMode = initialCenter == null
                )
            }
            rendererGate.publish(renderer)
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                registerSurfaceCallback()
                rendererGate.resume()
                startObserving()
            }
            override fun onStop(owner: LifecycleOwner) {
                stopObserving()
                rendererGate.pause()
                // Release the surface: a screen stopped underneath a pushed
                // screen never gets onSurfaceDestroyed (the host notifies only
                // the current callback), so the stale surface would keep the
                // host's buffer queue held and break the next screen's
                // surface. Release on stop, re-acquire on start. No-op while
                // the renderer is still initializing.
                rendererGate.releaseSurface()
                // NOTE: no unregisterSurfaceCallback() here — see
                // FreeDrivingScreen: an onStop unregister nulls the callback
                // the next screen registered (car-app starts the new screen
                // before stopping the old one), forcing a second surface
                // delivery that the host locks. Unregister only on destroy.
            }
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterSurfaceCallback()
                // Cancel a still-running init first, so no renderer work
                // continues after the screen is destroyed; the gate shuts
                // down a published renderer (spec: "Renderer still
                // initializing when the screen stops").
                rendererInitJob?.cancel()
                rendererGate.destroy()
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
     * (re-center on GPS), north-up orientation and the browse vehicle anchor
     * row (the street pill re-places with it — design D7). Settings are
     * re-read periodically so changes from the settings screen take effect
     * without a screen restart.
     */
    private fun applySettings(settings: com.naviveylin.core.AutoSettings) {
        browseAnchor = VehicleAnchorPosition.fromId(settings.freeDrivingAnchorId)
        // When opened via the details "Show" action (initialCenter set), the
        // map must stay centered on the requested destination — follow mode
        // would snap it back to the current GPS position.
        if (settings.followMode && !rendererGate.isFollowMode() && initialCenter == null) {
            Log.d(TAG, "settings: follow mode on -> re-center")
            rendererGate.reCenter()
        }
        if (settings.freeFormNorthUp) {
            val r = rendererGate.rendererOrNull()
            if (r != null) {
                val vp = r.viewportState.value
                if (vp.angle != 0.0) {
                    Log.d(TAG, "settings: north-up -> reset angle")
                    rendererGate.setViewport(vp.lat, vp.lon, vp.zoom, 0.0, vp.zoomFraction)
                }
            }
            // Renderer not ready yet: it is constructed with angle 0.0 (north-up).
        }
        // Apply the shared map style (loadStyleSheet blocks on the native DB
        // thread — run off the main thread; deduped by the applier).
        val style = settings.styleSheet
        scope.launch(Dispatchers.Default) {
            if (!styleApplier.apply(style)) {
                Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
            } else {
                // DB is ready (style loaded) — (re)push the day/night flag:
                // the startup push may have been dropped while the DB was still
                // initializing (warmup race), leaving the map on the wrong variant.
                pushDark()
            }
        }
    }

    /**
     * (Re)push the resolved dark presentation to the native stylesheet and
     * force a full render. The applier is reset first so a push that was
     * silently dropped by the native side (DB not initialized) is not deduped
     * away.
     */
    private fun pushDark() {
        // Skip until the renderer exists: the native client may still be
        // building off the main thread, and applying the stylesheet flag must
        // never first-touch it here. Re-pushed from the readiness observer and
        // after surface arrival once the init coroutine built the client.
        if (rendererGate.rendererOrNull() == null) return
        daylightApplier.reset()
        if (daylightApplier.apply(resolvedDark.value)) {
            rendererGate.invalidateStyle()
        }
    }

    override fun onGetTemplate(): Template {
        return try {
            val template = buildTemplate()
            DiagnosticsLog.log("MAP", "MapTemplate delivered")
            template
        } catch (e: Exception) {
            DiagnosticsLog.logThrowable(TEMPLATE_TAG, "MapTemplate build failed", e)
            SafeScreen.errorTemplate(carContext, e.message)
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
                            val renderer = rendererGate.rendererOrNull() ?: return@onPanModeChanged
                            val vp = renderer.viewportState.value
                            rendererGate.setViewport(vp.lat, vp.lon, vp.zoom, vp.angle, vp.zoomFraction)
                        }
                }
            })
            .build()

        // Content box = app menu.
        val content = MapTemplateFactory.buildMenuContent(
            carContext = carContext,
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
                // The map screen knows its viewport center, which is the search
                // distance reference when no GPS fix exists (spec:
                // search-result-ranking — distance reference).
                screenManager.push(
                    SearchScreen(
                        carContext,
                        navigationViewModel,
                        viewportCenter = { rendererGate.renderer.value?.markerViewport() }
                    )
                )
            })
            .addAction(MapStripActions.settingsAction {
                screenManager.push(
                    PreferencesScreen(carContext, onDarkModeChanged = onDarkModeChanged)
                )
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
                // render. Only safe on the main thread once the client is built
                // (renderer published); a pre-ready DPI is applied by the
                // off-main init coroutine and replayed with the surface.
                if (rendererGate.rendererOrNull() != null) {
                    runCatching { entryPoint.autoClientProvider().client().setMapDpi(surfaceDpi) }
                        .onFailure { Log.w(TAG, "setMapDpi failed", it) }
                }
                rendererGate.onSurfaceAvailable(surface, surfaceWidth, surfaceHeight, surfaceDpi)
                // Host chrome (the AAOS status/task bars) is derived from the
                // host's stable area once delivered; until then the insets are 0.
                rendererGate.setHostTopInset(hostInsetsPx().first)
                rendererGate.setHostBottomInset(hostInsetsPx().second)
                // Re-push the resolved day/night flag before the first render: the
                // startup push may have been dropped while the DB was still
                // initializing (warmup race).
                pushDark()
                // "Show location" maps: draw the destination marker and center
                // it in the visible map area (the host's menu panel covers the
                // left ~40% of the surface).
                initialCenter?.let { (clat, clon) ->
                    rendererGate.setDestinationMarker(clat, clon, initialDestinationName)
                    val rtl = carContext.resources.configuration.layoutDirection ==
                        View.LAYOUT_DIRECTION_RTL
                    val (vlat, vlon) = paneOffsetCenter(
                        clat, clon, initialZoom.toDouble(),
                        surfaceWidth, surfaceHeight, surfaceDpi, rtl
                    )
                    rendererGate.setViewport(vlat, vlon, initialZoom, 0.0)
                }
            }

            override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                Log.d(TAG, "Map surface destroyed")
                surfaceWidth = 0
                surfaceHeight = 0
                surfaceDpi = DEFAULT_DPI
                stableArea.setEmpty()
                visibleArea.setEmpty()
                rendererGate.setHostTopInset(0)
                rendererGate.setHostBottomInset(0)
                rendererGate.onSurfaceDestroyed()
            }

            override fun onVisibleAreaChanged(visible: Rect) {
                // Pill TOP edge from the currently-visible top (real coverage);
                // the stable area can over-reserve a top band the host never
                // draws (design D8 revision, street-name-host-views).
                visibleArea.set(visible)
                val (topInset, _) = hostInsetsPx()
                Log.d(TAG, "visible area $visible -> pillTopInset=$topInset")
                rendererGate.requestRender()
            }

            override fun onStableAreaChanged(newStableArea: Rect) {
                stableArea.set(newStableArea)
                val (topInset, bottomInset) = hostInsetsPx()
                Log.d(TAG, "stable area $newStableArea -> topInset=$topInset bottomInset=$bottomInset")
                // The street pill anchors inside the guaranteed-visible band
                // (design D8) — the AAOS bars are host chrome, not surface.
                rendererGate.setHostTopInset(topInset)
                rendererGate.setHostBottomInset(bottomInset)
                rendererGate.requestRender()
            }

            override fun onScroll(distanceX: Float, distanceY: Float) {
                // Convert scroll to viewport change. Use the same DPI as the
                // native render (not the surface DPI) so the pan tracks the
                // finger 1:1. Positive deltas move the map with the finger.
                // Gestures can only arrive with a surface, which implies the
                // renderer is (nearly always) published; before that, no-op.
                val renderer = rendererGate.rendererOrNull() ?: return
                val vp = renderer.viewportState.value
                val (newLat, newLon) = ProjectionUtils.dragDeltaToNewCenterRotated(
                    distanceX.toDouble(), distanceY.toDouble(),
                    vp.angle,
                    vp.zoomFraction,
                    surfaceWidth.toDouble(), surfaceHeight.toDouble(),
                    vp.lat, vp.lon,
                    renderer.projectionDpi
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
                rendererGate.setViewport(newLat, newLon, vp.zoom, vp.angle)
            }

            override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
                val renderer = rendererGate.rendererOrNull() ?: return
                // Ignore jitter so tiny finger spread during a pan never triggers a zoom step
                if (abs(scaleFactor - 1f) < SCALE_JITTER_THRESHOLD) return

                val vp = renderer.viewportState.value
                // Continuous zoom accumulation: each onScale event nudges the
                // fractional zoom by log2(scaleFactor); only whole-level changes render.
                val (newFraction, newZoom) = renderer.zoomStep(scaleFactor)
                if (newZoom == vp.zoom && newFraction == renderer.fractionalZoom()) return

                // Host may report an unavailable focal point (negative coords, e.g.
                // rotary-knob zoom) — zoom around the screen center in that case.
                val fx = if (focusX >= 0f) focusX.toDouble() else surfaceWidth / 2.0
                val fy = if (focusY >= 0f) focusY.toDouble() else surfaceHeight / 2.0
                val (newLat, newLon) = ProjectionUtils.zoomAtCursor(
                    fx, fy,
                    vp.zoom.toDouble(), newFraction,
                    surfaceWidth.toDouble(), surfaceHeight.toDouble(),
                    vp.lat, vp.lon,
                    renderer.projectionDpi
                )
                Log.d(TAG, "onScale focus=($fx,$fy) factor=$scaleFactor -> mag=$newZoom center=$newLat,$newLon")
                rendererGate.setViewport(newLat, newLon, newZoom, vp.angle, newFraction)
            }

            override fun onClick(x: Float, y: Float) {
                val renderer = rendererGate.rendererOrNull() ?: return
                // Convert screen coordinates to geo coordinates
                val vp = renderer.viewportState.value
                val (lat, lon) = ProjectionUtils.screenToGeo(
                    x.toDouble(), y.toDouble(),
                    surfaceWidth, surfaceHeight,
                    vp.zoom.toDouble(), vp.lat, vp.lon,
                    renderer.projectionDpi
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
     * Host-covered bands at the surface top/bottom (px) that the pill anchors
     * inside (design D8): the TOP inset is the currently-visible top (real
     * coverage; fallback: stable-area top, then 0) — the stable area can
     * over-reserve a top band the host never draws; the BOTTOM inset stays
     * from the stable area (its reported band matches the real task bar).
     * (0, 0) when unknown.
     */
    private fun hostInsetsPx(): Pair<Int, Int> {
        if (surfaceHeight <= 0) return 0 to 0
        return HostInsets.topInset(visibleArea, stableArea) to
            HostInsets.fromStableArea(surfaceHeight, stableArea).second
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
        val mag = rendererGate.rendererOrNull()?.viewportState?.value?.zoom ?: initialZoom
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
                    rendererGate.setGpsMarker(
                        pos.lat, pos.lon, pos.bearing, pos.accuracy,
                        speedKmH = pos.speedKmH,
                        timeMs = System.currentTimeMillis()
                    )
                    // Current street for the browse pill (spec: auto/browse):
                    // throttled bearing-aware road lookup at the GPS position.
                    resolveStreetName(pos)
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
                rendererGate.setFavoriteLocations(allFavorites)
            }
        }

        // Resolved day/night: push the stylesheet `daylight` flag and re-render
        // on change (tunnel entry, dusk, or a dark mode preference change on the
        // car). Deduped by the applier; the native side reloads the variant on
        // its DB thread. invalidateStyle forces a full
        // render so the stale-variant overrun buffer is never blitted.
        scope.launch {
            resolvedDark.collect { dark ->
                rendererGate.setDarkPresentation(dark)
                // Stylesheet flag applies only once the client is built; a
                // pre-ready dark is re-pushed by the readiness observer via
                // pushDark() (which skips until ready).
                if (rendererGate.rendererOrNull() != null && daylightApplier.apply(dark)) {
                    rendererGate.invalidateStyle()
                }
            }
        }

        // Basemap data changes (download/update/delete while the app runs):
        // re-render without an app restart, bypassing the overrun blit
        // (spec: basemap-loading — current view re-renders with/without the
        // basemap overlay active).
        scope.launch {
            entryPoint.basemapReloadNotifier().revision.collect { revision ->
                if (revision > 0L) {
                    rendererGate.invalidateData()
                }
            }
        }
    }

    /**
     * Throttled street-name resolution for the browse pill (spec:
     * auto/browse — current street derived from a bearing-aware road lookup
     * at the GPS position, throttled to avoid a lookup on every GPS tick).
     * Bearing-aware native lookup off the main thread, guarded by the shared
     * throttle ([StreetNameUpdater]); unnamed roads clear the label (no stale
     * text). Mirrors [FreeDrivingScreen].
     */
    private fun resolveStreetName(pos: AutoPosition) {
        if (!streetNameUpdater.shouldGeocode(pos.lat, pos.lon)) return
        if (streetJob?.isActive == true) return
        streetJob = scope.launch {
            val road = withContext(Dispatchers.Default) {
                try {
                    entryPoint.autoClientProvider().client().getRoadAt(pos.lat, pos.lon, pos.bearing)
                } catch (e: Exception) {
                    Log.w(TAG, "road info lookup failed", e)
                    null
                }
            }
            if (road != null) {
                streetNameUpdater.markGeocoded(pos.lat, pos.lon)
            }
            val name = road?.let { StreetNameUpdater.roadDisplayText(it.ref, it.name) }
            val changed = name != streetName
            streetName = name
            rendererGate.requestRender()
            if (changed) {
                Log.d(TAG, "browse street name -> $name")
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun onZoomIn() {
        val renderer = rendererGate.rendererOrNull() ?: return
        val current = renderer.viewportState.value
        val zoom = (current.zoom + 1).coerceAtMost(AutoMapRenderer.MAX_ZOOM)
        rendererGate.setViewport(current.lat, current.lon, zoom, current.angle, zoom.toDouble())
    }

    private fun onZoomOut() {
        val renderer = rendererGate.rendererOrNull() ?: return
        val current = renderer.viewportState.value
        val zoom = (current.zoom - 1).coerceAtLeast(AutoMapRenderer.MIN_ZOOM)
        rendererGate.setViewport(current.lat, current.lon, zoom, current.angle, zoom.toDouble())
    }

    companion object {
        private const val TAG = "MapScreen"
        private const val TEMPLATE_TAG = "TEMPLATE"
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
