@file:Suppress("DEPRECATION") // MapTemplate: no content-free map template exists in the car-app API

package com.naviveylin.auto

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
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
import com.naviveylin.core.CarSurfaceOwner
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.core.VehicleAnchorPosition
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val scope = carScreenScope("MapScreen")
    private var rendererInitJob: Job? = null

    private val entryPoint = EntryPointAccessors.fromApplication(
        carContext.applicationContext,
        AutoEntryPoint::class.java
    )
    private val favoritesProvider = entryPoint.autoFavoritesProvider()
    private val locationProvider = entryPoint.autoLocationProvider()
    private val settingsProvider = entryPoint.autoSettingsProvider()

    /**
     * Session-scoped car surface owner (spec: car-host-fault-isolation — Single-owner
     * car surface): the session's host forwards the car surface and gesture events to
     * this screen while it is attached, and the host — not the screen — releases the
     * surface, so a transition or a background round trip keeps the surface usable.
     */
    private val surfaceHost = entryPoint.autoSurfaceHost()

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

    /**
     * Shared-state observations for this screen (spec: auto/screen-observation;
     * design D2): [CarScreenObservations] owns how long each observation lives,
     * [MapScreenObservations] owns what is observed. Started in `onStart`, stopped in
     * `onStop`, so a background round trip or a push/pop cycle can never leave a
     * duplicate observer behind.
     */
    private val observations = CarScreenObservations()
    private val screenObservations = MapScreenObservations(
        observations = observations,
        locationProvider = locationProvider,
        favoritesProvider = favoritesProvider,
        basemapNotifier = entryPoint.basemapReloadNotifier(),
        resolvedDark = resolvedDark,
        onFix = ::onGpsFix,
        onFavorites = { rendererGate.setFavoriteLocations(it) },
        onDark = {
            // The overlay palette (vehicle marker, compass rose) follows the applied
            // stylesheet variant, not this value: [setDarkPresentation] is called by the
            // daylight collector below once the variant actually changed, so no frame can
            // show a night-palette overlay on a daylight map (spec: auto-map-layout —
            // Compass rose follows the resolved surface presentation).
            //
            // Stylesheet flag applies only once the client is built; a pre-ready dark
            // is re-pushed by the readiness observer via pushDark() (which skips until
            // ready). Deduped by value: an unchanged resolved value neither reloads
            // the variant nor forces a full render.
            pushDark()
        },
        onBasemapRevision = { rendererGate.invalidateData() }
    )

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
                    // Reported from the render thread; the car-app library wants the
                    // refresh on the main thread (spec: car-host-fault-isolation —
                    // Template invalidation is main-thread only).
                    postTemplateRefresh {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                            surfaceRefreshAttempts < MAX_SURFACE_REFRESH_ATTEMPTS
                        ) {
                            surfaceRefreshAttempts++
                            Log.w(TAG, "surface failed — invalidating to request a fresh surface (attempt $surfaceRefreshAttempts)")
                            invalidate()
                        }
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
            // Everything that touches the native client stays on a background
            // dispatcher (spec: auto-map-renderer — Renderer initialization off the
            // car-app main thread).
            val prepared = withContext(Dispatchers.Default) {
                val client = entryPoint.autoClientProvider().client()
                val viewport = resolveInitialAutoViewport(
                    mapsRootDir = File(carContext.filesDir, "maps"),
                    client = client,
                    initialCenter = initialCenter,
                    initialZoom = initialZoom,
                    defaultZoom = DEFAULT_AA_ZOOM,
                    defaultCenter = DEFAULT_LAT to DEFAULT_LON
                )
                Log.d(TAG, "AA renderer ready at ${viewport.lat},${viewport.lon} mag=${viewport.zoom}")
                client to viewport
            }
            // Constructed and published on the main thread with no suspension in
            // between, so a cancellation during the background work can never drop an
            // already-constructed renderer (spec: auto-map-renderer — "Renderer
            // constructed while the screen is being destroyed"). The constructor does
            // no native work: it initialises fields and its own loops only.
            //
            // The renderer starts with the car display's density and adopts the delivered
            // surface DPI — all overlay math (gestures, GPS marker) and every render request
            // use that one value (spec: `render-projection-dpi`).
            rendererGate.publish(
                AutoMapRenderer(
                    prepared.first,
                    carContext.resources.displayMetrics.densityDpi.toDouble(),
                    prepared.second.lat,
                    prepared.second.lon,
                    prepared.second.zoom,
                    // "Show location" maps must stay on the requested
                    // destination — follow mode would snap the viewport to
                    // every GPS fix.
                    initialFollowMode = initialCenter == null
                )
            )
        }

        // Stylesheet day/night pushes (spec: car-host-fault-isolation — Host callbacks
        // answer promptly): the request is published by pushDark, and the native flag is set
        // here, off the main thread — including the one the host's surface delivery asks for.
        scope.launch {
            rendererGate.daylightPush.collect { request ->
                if (request == null) return@collect
                val applied = withContext(Dispatchers.Default) {
                    runCatching { daylightApplier.apply(request.dark, request.force) }
                        .onFailure { Log.w(TAG, "setStyleSheetFlag failed", it) }
                        .getOrDefault(false)
                }
                // A changed variant invalidates the rendered frame (the overrun buffer holds
                // the previous variant); the gate's own thread is the main one, so this
                // stays here. The overlay palette flips with it, so the app-drawn overlays
                // and the map always show the same presentation.
                if (applied) {
                    rendererGate.setDarkPresentation(request.dark)
                    rendererGate.invalidateStyle()
                }
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                surfaceRefreshAttempts = 0
                surfaceHost.attach(surfaceOwner)
                rendererGate.resume()
                screenObservations.start()
            }
            override fun onStop(owner: LifecycleOwner) {
                // Every observation ends with the started period (spec:
                // auto/screen-observation — One instance of each observation per
                // started period).
                observations.stop()
                rendererGate.pause()
                // Stop drawing on the session's surface: the host starts the incoming
                // screen before it stops this one, so a renderer that kept the surface
                // reference could lock the one session surface while the incoming
                // screen draws through it. Dropping it also releases this screen's
                // overrun frame buffer (spec: auto-map-renderer — A stopped renderer
                // holds no surface or frame buffer). Detach only: the session owns the
                // surface's lifetime, so a screen that stops underneath a pushed screen
                // neither clears the new screen's surface nor releases the queue it
                // draws through (spec: car-host-fault-isolation — Single-owner car
                // surface). No-op while the renderer is still initializing.
                rendererGate.detachSurface()
                surfaceHost.detach(surfaceOwner)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                surfaceHost.detach(surfaceOwner)
                // Cancel a still-running init first, so no renderer work
                // continues after the screen is destroyed; the gate shuts
                // down a published renderer (spec: "Renderer still
                // initializing when the screen stops").
                rendererInitJob?.cancel()
                rendererGate.destroy()
                // A destroy without a stop must not leave an observation running.
                observations.stop()
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
            val styleChanged = style != pushedForStyleSheet
            if (!styleApplier.apply(style)) {
                Log.w(TAG, "loadStyleSheet '$style' failed — previous style kept")
            } else {
                pushedForStyleSheet = style
                // Force the push only when the stylesheet was (re)loaded: that is the
                // DB-ready re-push for the warmup race. An unchanged settings re-read
                // must not reload the variant nor force a full render (spec:
                // car-host-fault-isolation — Bounded periodic render work).
                pushDark(force = styleChanged)
            }
        }
    }

    /**
     * Stylesheet whose day/night flag was pushed last: a *changed* stylesheet means the
     * native side reloaded the style variant, so the flag is re-pushed once. Written
     * from the settings re-read (background dispatcher), hence volatile.
     */
    @Volatile
    private var pushedForStyleSheet: String? = null

    /**
     * (Re)push the resolved dark presentation to the native stylesheet and force a full
     * render when it actually changed (spec: car-host-fault-isolation — Bounded
     * periodic render work; design D5): the settings re-read runs every few seconds and
     * must not reload the style variant plus a full native render each time.
     *
     * @param force re-push a value the native side may have dropped (the stylesheet was
     *   just (re)loaded, or the DB became ready after an earlier push)
     */
    private fun pushDark(force: Boolean = false) {
        // Skip until the renderer exists: the native client may still be
        // building off the main thread, and applying the stylesheet flag must
        // never first-touch it here. Re-pushed from the readiness observer and
        // after surface arrival once the init coroutine built the client.
        if (rendererGate.rendererOrNull() == null) return
        val dark = resolvedDark.value
        if (!daylightApplier.needsPush(dark, force)) return
        // Publish, do not push: setStyleSheetFlag reloads the style variant on the DB
        // thread, and this runs from the host's surface callback (spec:
        // car-host-fault-isolation — Host callbacks answer promptly). The collector started
        // in init applies it on a background dispatcher.
        rendererGate.requestDaylightPush(dark, force)
    }

    override fun onGetTemplate(): Template {
        return try {
            // No per-build log line: template builds are host-paced and unbounded,
            // and a host callback must not do filesystem work on its answering path
            // (spec: auto-diagnostics — Logging never blocks the caller, design D6).
            // A build failure still lands through logThrowable below.
            buildTemplate()
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

        // Content box = app menu. Every entry mutates the host stack from a click
        // callback, so each push runs through the guard: the car-app library rethrows an
        // app exception on the main thread (spec: car-host-fault-isolation — No fault
        // escapes into the host path).
        val content = MapTemplateFactory.buildMenuContent(
            carContext = carContext,
            onFreeDriving = {
                // Push the destination-free navigation-style view (spec:
                // auto/free-driving). Popping it returns to this map view.
                armScreenPush(carContext, scope, "FreeDrivingScreen") {
                    FreeDrivingScreen(carContext, resolvedDark)
                }
            },
            onStarredFavorites = {
                armScreenPush(carContext, scope, "FavoritesScreen (starred)") {
                    FavoritesScreen(carContext, navigationViewModel, starredOnly = true)
                }
            },
            onAllFavorites = {
                armScreenPush(carContext, scope, "FavoritesScreen") {
                    FavoritesScreen(carContext, navigationViewModel)
                }
            },
            onPoiSearch = {
                armScreenPush(carContext, scope, "PoiSearchScreen") {
                    PoiSearchScreen(carContext, navigationViewModel)
                }
            },
            onSearchHistory = {
                armScreenPush(carContext, scope, "SearchHistoryScreen") {
                    SearchHistoryScreen(carContext, navigationViewModel)
                }
            },
            onDiagnostics = {
                armScreenPush(carContext, scope, "DiagnosticsScreen") {
                    DiagnosticsScreen(carContext)
                }
            },
            onAbout = {
                armScreenPush(carContext, scope, "AboutScreen") {
                    AboutScreen(carContext)
                }
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
                armScreenPush(carContext, scope, "SearchScreen") {
                    SearchScreen(
                        carContext,
                        navigationViewModel,
                        viewportCenter = { rendererGate.renderer.value?.markerViewport() }
                    )
                }
            })
            .addAction(MapStripActions.settingsAction {
                armScreenPush(carContext, scope, "PreferencesScreen") {
                    PreferencesScreen(carContext, onDarkModeChanged = onDarkModeChanged)
                }
            })
            .addAction(MapStripActions.zoomInAction { onZoomIn() })
            .addAction(MapStripActions.zoomOutAction { onZoomOut() })
            .build()

        return MapTemplateFactory.buildTemplate(mapController!!, content, actionStrip)
    }

    /**
     * Car-surface owner for this screen (spec: car-host-fault-isolation — Single-owner
     * car surface; design D1). Attached to the session's [surfaceHost] on start and
     * detached on stop; the host hands over a retained surface immediately on attach.
     */
    private val surfaceOwner: CarSurfaceOwner = object : CarSurfaceOwner {
        override fun onCarSurfaceAvailable(surface: Surface, width: Int, height: Int, dpi: Double) {
            surfaceWidth = width
            surfaceHeight = height
            surfaceDpi = dpi.takeIf { it > 0.0 } ?: DEFAULT_DPI
            Log.d(TAG, "Map surface available: ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi")
            DiagnosticsLog.log(
                "MAP",
                "Surface available ${surfaceWidth}x${surfaceHeight} @ ${surfaceDpi}dpi"
            )
            // The delivered DPI reaches the renderer through the gate — a host callback
            // must not resolve the client (spec: car-host-fault-isolation — Host callbacks
            // answer promptly), and every render request carries the value.
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

            override fun onCarSurfaceDestroyed() {
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

            override fun onCarVisibleAreaChanged(visible: Rect) {
                // Pill TOP edge from the currently-visible top (real coverage);
                // the stable area can over-reserve a top band the host never
                // draws (design D8 revision, street-name-host-views).
                visibleArea.set(visible)
                val (topInset, _) = hostInsetsPx()
                Log.d(TAG, "visible area $visible -> pillTopInset=$topInset")
                rendererGate.requestRender()
            }

            override fun onCarStableAreaChanged(stable: Rect) {
                stableArea.set(stable)
                val (topInset, bottomInset) = hostInsetsPx()
                Log.d(TAG, "stable area $stable -> topInset=$topInset bottomInset=$bottomInset")
                // The street pill anchors inside the guaranteed-visible band
                // (design D8) — the AAOS bars are host chrome, not surface.
                rendererGate.setHostTopInset(topInset)
                rendererGate.setHostBottomInset(bottomInset)
                rendererGate.requestRender()
            }

            override fun onCarScroll(distanceX: Float, distanceY: Float) {
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

            override fun onCarScale(focusX: Float, focusY: Float, scaleFactor: Float) {
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

            override fun onCarClick(x: Float, y: Float) {
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
     *
     * Internal so the host-callback contract is unit-testable (spec:
     * car-host-fault-isolation — Host callbacks answer promptly): this is what
     * [CarSurfaceOwner.onCarClick] calls, and it must return without constructing
     * the native client.
     */
    internal fun onLocationSelected(lat: Double, lon: Double) {
        // Host-callback path: only state is retained here (spec: car-host-fault-isolation —
        // Host callbacks answer promptly). The native client is resolved inside the
        // background block — its first touch builds it — and the work runs in the screen's own
        // scope, which is cancelled on destroy (a per-tap scope is never reclaimed).
        val mag = rendererGate.rendererOrNull()?.viewportState?.value?.zoom ?: initialZoom
        // No ScreenManager resolution here: this runs on the host's click path, and a
        // failing resolution must not escape into the host (spec: car-host-fault-isolation
        // — Host callbacks answer promptly; No fault escapes into the host path). The
        // guarded calls below resolve it inside their own `try`.
        scope.launch {
            val candidates = withContext(Dispatchers.Default) {
                try {
                    entryPoint.autoClientProvider().client().getDescriptionCandidates(lat, lon, mag)
                } catch (e: Exception) {
                    Log.w(TAG, "getDescriptionCandidates failed", e)
                    emptyList()
                }
            }
            val screen = if (shouldShowCandidatePicker(candidates.size)) {
                CandidatePickerScreen(carContext, candidates) { desc ->
                    armScreenPush(carContext, scope, "DetailsScreen (tap candidate)") {
                        DetailsScreen(
                            carContext, navigationViewModel, lat, lon,
                            preloadedDescription = desc,
                            mag = mag
                        )
                    }
                }
            } else {
                DetailsScreen(
                    carContext, navigationViewModel, lat, lon,
                    preloadedDescription = candidates.firstOrNull(),
                    mag = mag
                )
            }
            guardedHostCall("push DetailsScreen (map tap)") { screenManager.push(screen) }
        }
    }


    /**
     * One GPS fix for the browse map (spec: auto/browse — the street pill follows
     * the position; auto/screen-observation — observed only while the screen is
     * started).
     */
    private fun onGpsFix(pos: AutoPosition) {
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
