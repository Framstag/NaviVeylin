package com.framstag.libosmscout.client

import com.naviveylin.core.BundledMapStyles
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Test double for [OSMScoutClient] that records style flag pushes instead of
 * calling into native code. Lives in the client package so it can access the
 * package-private constructor.
 */
class FakeOSMScoutClient : OSMScoutClient() {

    companion object {
        /** Colour every test render fills its pixels with; [createTestPixels] uses it. */
        val TEST_PIXEL_COLOR: Int = android.graphics.Color.rgb(200, 220, 240)
    }

    /** Recorded (key, value) style flag pushes in call order. */
    val styleFlags: MutableList<Pair<String, Boolean>> = CopyOnWriteArrayList()

    /** Number of [render] invocations (full direct native renders). */
    val renderCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** Number of [renderWithRouteAndPois] invocations (per-tile renders + overlay renders). */
    val renderWithRouteAndPoisCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** When true both render entry points return null (native render produced nothing). */
    @Volatile
    var renderReturnsNull: Boolean = false

    /** Optional artificial delay (ms) inside [renderWithRouteAndPois] — used to
     *  interleave mode switches with an in-flight tile render in tests. */
    var renderWithRouteAndPoisDelayMs: Long = 0L

    /** Search-selection marker latitude from the last [renderWithRouteAndPois] (NaN when unset). */
    var lastSearchSelLat: Double = Double.NaN

    /** Search-selection marker longitude from the last [renderWithRouteAndPois] (NaN when unset). */
    var lastSearchSelLon: Double = Double.NaN

    /** Route polyline latitudes from the last [renderWithRouteAndPois] (null when unset). */
    var lastRouteLats: DoubleArray? = null

    /** Route polyline longitudes from the last [renderWithRouteAndPois] (null when unset). */
    var lastRouteLons: DoubleArray? = null

    /** Magnification of the last render (either entry point; -1 until first render). */
    @Volatile
    var lastRenderMag: Double = -1.0

    /** Magnifications of every native render, in order (for render-order assertions). */
    val renderMags = java.util.concurrent.CopyOnWriteArrayList<Double>()

    /** Latitude of the last render (NaN until first render). */
    @Volatile
    var lastRenderLat: Double = Double.NaN

    /** Longitude of the last render (NaN until first render). */
    @Volatile
    var lastRenderLon: Double = Double.NaN

    override fun setStyleSheetFlag(key: String, value: Boolean) {
        styleFlags.add(key to value)
    }

    // --- Map style switching stubs ---

    /** Style names passed to [loadStyleSheet] in call order. */
    val styleSheetLoads: MutableList<String> = CopyOnWriteArrayList()

    /** Return value of the next [loadStyleSheet] call (true = success). */
    @Volatile
    var styleSheetLoadResult: Boolean = true

    /**
     * Style names whose load fails even while [styleSheetLoadResult] is true
     * (per-style failures, for the startup-fallback path).
     */
    val failingStyleSheetLoads: MutableSet<String> = CopyOnWriteArraySet()

    override fun loadStyleSheet(name: String): Boolean {
        styleSheetLoads.add(name)
        // The native client records the outcome of every load attempt (change
        // `client-style-load-resilience`); keep the two in sync so tests that
        // assert through `wasLastStyleLoadSuccessful` see the same result.
        styleLoadSuccessful = styleSheetLoadResult && name !in failingStyleSheetLoads
        return styleLoadSuccessful
    }

    /** Styles returned by [getAvailableStyleSheets] (default: user-selectable set). */
    var availableStyleSheetNames: List<String> = BundledMapStyles.USER_SELECTABLE

    override fun getAvailableStyleSheets(): List<String> = availableStyleSheetNames

    /** Name returned by [getActiveStyleSheet] (default: standard.oss). */
    var activeStyleSheetName: String = "standard.oss"

    override fun getActiveStyleSheet(): String = activeStyleSheetName

    /**
     * Result returned by [wasLastStyleLoadSuccessful] (default: true). Set to
     * false to simulate a rejected stylesheet on a load path that does not
     * report through [loadStyleSheet]'s return value (style-flag reload).
     */
    @Volatile
    var styleLoadSuccessful: Boolean = true

    override fun wasLastStyleLoadSuccessful(): Boolean = styleLoadSuccessful

    // --- Database / density stubs (needed to exercise initMap) ---

    /** Paths passed to [openDatabase] in call order. */
    val openedDatabases: MutableList<String> = CopyOnWriteArrayList()

    /** Result returned by [openDatabase]. */
    @Volatile
    var openDatabaseResult: Boolean = true

    override fun openDatabase(path: String): Boolean {
        openedDatabases.add(path)
        return openDatabaseResult
    }

    /**
     * Batch path lists passed to [openDatabases], in call order. Empty while the
     * caller registers directory by directory.
     */
    val openedDatabaseBatches: MutableList<List<String>> = CopyOnWriteArrayList()

    /** Result returned by [openDatabases], index-aligned with the batch it got. */
    @Volatile
    var openDatabasesResult: (List<String>) -> BooleanArray = { paths ->
        BooleanArray(paths.size) { true }
    }

    override fun openDatabases(paths: Array<String>): BooleanArray {
        val batch = paths.toList()
        openedDatabaseBatches.add(batch)
        return openDatabasesResult(batch)
    }

    /**
     * DPIs carried by every native render request, in call order (both entry
     * points) — the projection DPI is part of the request, never client state
     * (spec: `render-projection-dpi`).
     */
    val renderDpis = java.util.concurrent.CopyOnWriteArrayList<Double>()

    /** DPI of the last render (either entry point; NaN until first render). */
    @Volatile
    var lastRenderDpi: Double = Double.NaN

    /** Cache sizes passed to [setNativeDataCacheSize] in call order. */
    val nativeDataCacheSizes = mutableListOf<Int>()

    /**
     * When true [setNativeDataCacheSize] throws instead of recording (the bridge is gone, e.g. the
     * native library failed to load) — for the non-fatal paths of the cache policy.
     */
    @Volatile
    var nativeDataCacheSizeFails: Boolean = false

    override fun setNativeDataCacheSize(cacheSize: Int) {
        if (nativeDataCacheSizeFails) {
            throw IllegalStateException("bridge is gone")
        }
        nativeDataCacheSizes.add(cacheSize)
    }

    /** Bounding box returned by [getDatabaseBoundingBox] (null = none). */
    var databaseBoundingBox: DoubleArray? = null

    override fun getDatabaseBoundingBox(path: String): DoubleArray? = databaseBoundingBox

    // --- Basemap stubs ---

    /** Number of [reloadBasemap] invocations. */
    var reloadBasemapCount = 0

    override fun reloadBasemap() {
        reloadBasemapCount++
    }

    /** Directories passed to [setBasemapLookupDirectory] in call order. */
    val basemapLookupDirectories = mutableListOf<String>()

    override fun setBasemapLookupDirectory(directory: String) {
        basemapLookupDirectories.add(directory)
    }

    override fun render(
        width: Int, height: Int,
        lat: Double, lon: Double,
        angle: Double, magnification: Double,
        dpi: Double
    ): IntArray? {
        renderCount.incrementAndGet()
        lastRenderLat = lat
        lastRenderLon = lon
        lastRenderMag = magnification
        renderMags.add(magnification)
        lastRenderDpi = dpi
        renderDpis.add(dpi)
        if (renderReturnsNull) return null
        return createTestPixels(width, height)
    }

    override fun renderWithRouteAndPois(
        width: Int, height: Int,
        lat: Double, lon: Double, angle: Double, magnification: Double,
        dpi: Double,
        routeLats: DoubleArray?, routeLons: DoubleArray?,
        favoriteLats: DoubleArray?, favoriteLons: DoubleArray?,
        searchSelLat: Double, searchSelLon: Double,
        trackLats: DoubleArray?, trackLons: DoubleArray?
    ): IntArray? {
        renderWithRouteAndPoisCount.incrementAndGet()
        lastSearchSelLat = searchSelLat
        lastSearchSelLon = searchSelLon
        lastRouteLats = routeLats
        lastRouteLons = routeLons
        lastRenderLat = lat
        lastRenderLon = lon
        lastRenderMag = magnification
        renderMags.add(magnification)
        lastRenderDpi = dpi
        renderDpis.add(dpi)
        if (renderWithRouteAndPoisDelayMs > 0L) {
            Thread.sleep(renderWithRouteAndPoisDelayMs)
        }
        if (renderReturnsNull) return null
        return createTestPixels(width, height)
    }

    private fun createTestPixels(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        pixels.fill(TEST_PIXEL_COLOR)
        return pixels
    }

    /** Number of [renderInto] invocations (the caller-owned-buffer render path). */
    val renderIntoCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** When true [renderInto] reports no frame without writing (native render produced nothing). */
    @Volatile
    var renderIntoReturnsFalse: Boolean = false

    /**
     * The buffer-taking render path: the same request and the same pixels as the allocating one,
     * written into the caller's storage. The fake answers through the same recorders the two
     * allocating entry points use — and, like the old seam, through the PLAIN recorder when the
     * request carries no overlay arrays — so the request assertions of the renderer suites keep
     * their meaning for this path.
     */
    override fun renderInto(
        width: Int, height: Int,
        lat: Double, lon: Double, angle: Double, magnification: Double,
        dpi: Double,
        routeLats: DoubleArray?, routeLons: DoubleArray?,
        favoriteLats: DoubleArray?, favoriteLons: DoubleArray?,
        searchSelLat: Double, searchSelLon: Double,
        trackLats: DoubleArray?, trackLons: DoubleArray?,
        pixels: java.nio.ByteBuffer
    ): Boolean {
        renderIntoCount.incrementAndGet()
        val hasOverlays = (favoriteLats != null && favoriteLats.isNotEmpty()) ||
            !searchSelLat.isNaN() ||
            (routeLats != null && routeLats.isNotEmpty()) ||
            (trackLats != null && trackLons != null)
        val rendered = if (hasOverlays) {
            renderWithRouteAndPois(
                width, height, lat, lon, angle, magnification, dpi,
                routeLats, routeLons, favoriteLats, favoriteLons, searchSelLat, searchSelLon,
                trackLats, trackLons
            )
        } else {
            render(width, height, lat, lon, angle, magnification, dpi)
        } ?: return false
        if (renderIntoReturnsFalse) return false
        if (pixels.capacity() < rendered.size * 4) return false
        val target = pixels.asIntBuffer()
        target.clear()
        target.put(rendered)
        return true
    }

    // --- Route calculation / navigation stubs (used by car-only route fallback tests) ---

    /** Route delivered by [calculateRouteWithProfile]; null → [deliverRouteError]. */
    var routeToDeliver: RouteEntry? = null

    /** Navigation listener captured from the last [startNavigationWithVehicle] call. */
    @Volatile
    var navigationListener: NavigationListener? = null

    /** Number of [startNavigationWithVehicle] invocations (native controllers started). */
    @Volatile
    var navigationStartCount = 0

    /** Profile of the last [calculateRouteWithProfile] call. */
    @Volatile
    var lastRouteProfile: RoutingProfile? = null

    /** (lat, lon) of the last [calculateRouteWithProfile] call. */
    @Volatile
    var lastRouteStart: Pair<Double, Double>? = null

    /** (lat, lon) destination of the last [calculateRouteWithProfile] call. */
    @Volatile
    var lastRouteDest: Pair<Double, Double>? = null

    /** Error text delivered when [routeToDeliver] is null. */
    var deliverRouteError: String? = null

    /**
     * Throwable thrown synchronously by [calculateRouteWithProfile] instead of
     * delivering a route. Models a failure at the engine's native boundary: pass an
     * `Error` (e.g. `UnsatisfiedLinkError`) to prove the boundary confines more than
     * exceptions (spec: `navigation-engine` — Native failure is confined, not fatal).
     */
    var routeCalculationError: Throwable? = null

    /** Number of [calculateRouteWithProfile] invocations. */
    @Volatile
    var routeCalculationCount = 0

    override fun calculateRouteWithProfile(
        startLat: Double, startLon: Double,
        destLat: Double, destLon: Double,
        profile: RoutingProfile,
        callback: RouteCallback
    ) {
        routeCalculationCount++
        routeCalculationError?.let { throw it }
        lastRouteProfile = profile
        lastRouteStart = startLat to startLon
        lastRouteDest = destLat to destLon
        val route = routeToDeliver
        if (route != null) {
            callback.onSuccess(route)
        } else {
            callback.onError(deliverRouteError ?: "No route available")
        }
    }

    var navigationStartError: Throwable? = null

    override fun startNavigationWithVehicle(
        routeHandle: Long,
        vehicle: Vehicle,
        listener: NavigationListener
    ): NavigationController? {
        navigationStartCount++
        navigationStartError?.let { throw it }
        navigationListener = listener
        return null
    }

    override fun getDescription(
        lat: Double, lon: Double, magnification: Int
    ): ObjectDescription? {
        return null
    }

    /** Coordinates passed to [getDescriptionCandidates] in call order. */
    val candidateLookupCoords = mutableListOf<Pair<Double, Double>>()

    /** Results returned by the next [getDescriptionCandidates] call (default: empty). */
    var nextCandidateDescriptions: List<ObjectDescription> = emptyList()

    override fun getDescriptionCandidates(
        lat: Double, lon: Double, magnification: Int
    ): List<ObjectDescription> {
        candidateLookupCoords.add(lat to lon)
        return nextCandidateDescriptions
    }

    /** Address returned by [getAddressAt] (null = no indexed address). */
    var addressAt: Array<String>? = null

    override fun getAddressAt(lat: Double, lon: Double): Array<String>? = addressAt

    /** Value returned by [getMaxSpeedAt] (negative = no limit, like native). */
    @Volatile
    var maxSpeedAt: Double = -1.0

    /** When set, [getMaxSpeedAt] throws this instead of returning. */
    var maxSpeedAtError: Exception? = null

    /** Coordinates passed to [getMaxSpeedAt] in call order. */
    val maxSpeedLookupCoords = mutableListOf<Pair<Double, Double>>()

    override fun getMaxSpeedAt(lat: Double, lon: Double): Double {
        maxSpeedLookupCoords.add(lat to lon)
        maxSpeedAtError?.let { throw it }
        return maxSpeedAt
    }

    /** Road returned by [getRoadAt] (null = no road found). */
    @Volatile
    var roadAt: RoadInfo? = null

    /** When set, [getRoadAt] throws this instead of returning. */
    /** Throwable thrown by [getRoadAt]; an `Error` models a native failure. */
    var roadAtError: Throwable? = null

    /** (lat, lon, bearing) passed to [getRoadAt] in call order. */
    val roadAtLookupCalls = mutableListOf<Triple<Double, Double, Double>>()

    override fun getRoadAt(lat: Double, lon: Double, bearing: Double): RoadInfo? {
        roadAtLookupCalls.add(Triple(lat, lon, bearing))
        roadAtError?.let { throw it }
        return roadAt
    }

    /** Handles passed to [searchLocations] in call order. */
    val searchAdminRegionHandles = mutableListOf<Long>()

    /** Queries passed to [searchLocations] in call order. */
    val searchQueries = mutableListOf<String>()

    /** Limits passed to [searchLocations] in call order. */
    val searchLimits = mutableListOf<Int>()

    /** Results returned by the next [searchLocations] call (default: empty). */
    var nextSearchResults: Array<LocationEntry>? = emptyArray()

    /**
     * Per-query results overriding [nextSearchResults] for matching queries —
     * lets a test script different answers for different steps of a fallback
     * chain. Queries not present fall back to [nextSearchResults].
     */
    val searchResultsByQuery = mutableMapOf<String, Array<LocationEntry>?>()

    /** When set, [searchLocations] throws this instead of returning. */
    var searchLocationsError: Exception? = null

    override fun searchLocations(query: String, limit: Int, adminRegionHandle: Long): Array<LocationEntry>? {
        searchAdminRegionHandles.add(adminRegionHandle)
        searchQueries.add(query)
        searchLimits.add(limit)
        searchLocationsError?.let { throw it }
        return searchResultsByQuery[query] ?: nextSearchResults
    }

    /** (adminRegion, postalArea, location, address) passed to [searchLocationByForm] in call order. */
    val formSearchArgs = mutableListOf<List<String>>()

    /** Results returned by the next [searchLocationByForm] call (default: empty). */
    var nextFormResults: Array<LocationEntry>? = emptyArray()

    /**
     * When non-empty, [searchLocationByForm] consumes one entry per call
     * (FIFO) instead of returning [nextFormResults] — lets a test script a
     * street-less first attempt followed by a hit.
     */
    val formResultsQueue = mutableListOf<Array<LocationEntry>?>()

    /** When set, [searchLocationByForm] throws this instead of returning. */
    var formSearchError: Exception? = null

    override fun searchLocationByForm(
        adminRegion: String,
        postalArea: String,
        location: String,
        address: String,
        limit: Int
    ): Array<LocationEntry>? {
        formSearchArgs.add(listOf(adminRegion, postalArea, location, address))
        formSearchError?.let { throw it }
        if (formResultsQueue.isNotEmpty()) {
            return formResultsQueue.removeAt(0)
        }
        return nextFormResults
    }

    // --- POI search stubs ---

    /** Categories passed to [searchPOIs] in call order. */
    val poiSearchCategories = mutableListOf<String>()

    /** Type-name lists passed to [searchPOIsByTypes] in call order. */
    val poiSearchTypeNames = mutableListOf<List<String>>()

    /** Results returned by [searchPOIs]/[searchPOIsByTypes] (default: empty). */
    var nextPoiResults: Array<PoiEntry>? = emptyArray()

    /** When set, [searchPOIs]/[searchPOIsByTypes] throw this instead of returning. */
    var poiSearchError: Exception? = null

    override fun searchPOIsByTypes(
        typeNames: Array<String>,
        lat: Double, lon: Double,
        radiusMeters: Double, limit: Int
    ): Array<PoiEntry>? {
        poiSearchTypeNames.add(typeNames.toList())
        poiSearchError?.let { throw it }
        return nextPoiResults
    }

    override fun searchPOIs(
        category: String,
        lat: Double, lon: Double,
        radiusMeters: Double, limit: Int
    ): Array<PoiEntry>? {
        poiSearchCategories.add(category)
        poiSearchError?.let { throw it }
        return nextPoiResults
    }

    /** Handles returned by [resolveAdminRegion] in call order. */
    val adminRegionHandles = mutableListOf<Long>()

    /** Handles released via [releaseAdminRegion] in call order. */
    val releasedAdminRegionHandles = mutableListOf<Long>()

    /** Handle returned by the next [resolveAdminRegion] call (0 = no region). */
    var nextAdminRegionHandle: Long = 42L

    override fun resolveAdminRegion(lat: Double, lon: Double): Long {
        adminRegionHandles.add(nextAdminRegionHandle)
        return nextAdminRegionHandle
    }

    override fun releaseAdminRegion(handle: Long) {
        releasedAdminRegionHandles.add(handle)
    }

    /** Name returned by [getAdminRegionName]. */
    var adminRegionName: String? = "Dortmund"

    override fun getAdminRegionName(handle: Long): String? = adminRegionName

    /** Name returned by [getAdminRegionScopeName]. Defaults to [adminRegionName]. */
    var adminRegionScopeName: String? = null

    override fun getAdminRegionScopeName(handle: Long): String? =
        adminRegionScopeName ?: adminRegionName

    override fun getObjectBoundingBox(
        lat: Double, lon: Double, magnification: Int
    ): DoubleArray? {
        return null
    }

    // --- In-memory favorites CRUD (mirrors C++ FavoriteLocationService) ---

    private val favGroups = mutableListOf<FavoriteLocationGroup>()

    /** Number of [saveFavoriteLocations] invocations (one per persisted write). */
    val saveFavoriteLocationsCalls = java.util.concurrent.atomic.AtomicInteger(0)

    /** Recorded move requests as (group name, favorite name, target index). */
    val moveFavoriteCalls: MutableList<Triple<String, String, Int>> = CopyOnWriteArrayList()

    /** Set to false to simulate a failing native move. */
    var moveFavoriteResult: Boolean = true

    /** One group-order move request as it reached the native store. */
    data class MoveGroupCall(
        val groupName: String,
        val newIndex: Int,
        /** Name of the thread that made the call — used to prove it is not the main thread. */
        val threadName: String = ""
    )

    /** Recorded group-order move requests, in call order. */
    val moveGroupCalls: MutableList<MoveGroupCall> = CopyOnWriteArrayList()

    /** Set to false to simulate a failing native group move. */
    var moveGroupResult: Boolean = true

    /** One cross-group move request as it reached the native store. */
    data class MoveToGroupCall(
        val sourceGroup: String,
        val favName: String,
        val targetGroup: String,
        val newIndex: Int,
        /** Name of the thread that made the call — used to prove it is not the main thread. */
        val threadName: String = ""
    )

    /** Recorded cross-group move requests, in call order. */
    val moveFavoriteToGroupCalls: MutableList<MoveToGroupCall> = CopyOnWriteArrayList()

    /** Set to false to simulate a failing native cross-group move. */
    var moveFavoriteToGroupResult: Boolean = true

    /**
     * Favorite order handed to the last [saveFavoriteLocations] call, as
     * (group name, favorite names in save order). The native save path rebuilds
     * its store from this array, so the recorded order is what would be written.
     */
    var lastSavedFavoriteOrder: List<Pair<String, List<String>>> = emptyList()

    override fun loadFavoriteLocations(filePath: String): Boolean {
        favGroups.clear()
        return true
    }

    /**
     * Optional hook invoked at the very start of [saveFavoriteLocations],
     * before the call is recorded. Lets a test park a write inside its persist
     * step and run another write during that window (the window the write
     * serialisation in `FavoriteRepository` has to close).
     */
    var beforeSaveFavoriteLocations: (() -> Unit)? = null

    override fun saveFavoriteLocations(
        filePath: String,
        groups: Array<out FavoriteLocationGroup>
    ): Boolean {
        beforeSaveFavoriteLocations?.invoke()
        saveFavoriteLocationsCalls.incrementAndGet()
        lastSavedFavoriteOrder = groups.map { group ->
            group.name to group.favorites.map { it.name }
        }
        return true
    }

    override fun getFavoriteGroups(): Array<FavoriteLocationGroup> =
        // Fresh objects per call, like the JNI bridge (which reconstructs them
        // from the native store). Without this, an attribute-only change (e.g.
        // starring) yields an equal map and the repository's StateFlow collapses
        // the emission, so the UI would never see the change.
        favGroups.map { group ->
            FavoriteLocationGroup(group.name).also { groupCopy ->
                groupCopy.attributes.putAll(group.attributes)
                group.favorites.forEach { fav ->
                    FavoriteLocation(fav.name, fav.lat, fav.lon).also { favCopy ->
                        favCopy.attributes.putAll(fav.attributes)
                        groupCopy.favorites.add(favCopy)
                    }
                }
            }
        }.toTypedArray()

    override fun addGroup(name: String): Boolean {
        if (favGroups.any { it.name == name }) return false
        favGroups.add(FavoriteLocationGroup(name))
        return true
    }

    override fun deleteGroup(name: String): Boolean {
        val group = favGroups.firstOrNull { it.name == name } ?: return false
        favGroups.remove(group)
        return true
    }

    override fun renameGroup(oldName: String, newName: String): Boolean {
        val group = favGroups.firstOrNull { it.name == oldName } ?: return false
        if (favGroups.any { it.name == newName }) return false
        group.name = newName
        return true
    }

    override fun addFavorite(
        groupName: String, favName: String, lat: Double, lon: Double
    ): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        if (group.favorites.any { it.name == favName }) return false
        group.favorites.add(FavoriteLocation(favName, lat, lon))
        return true
    }

    override fun deleteFavorite(groupName: String, favName: String): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        val fav = group.favorites.firstOrNull { it.name == favName } ?: return false
        group.favorites.remove(fav)
        return true
    }

    override fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        val fav = group.favorites.firstOrNull { it.name == oldName } ?: return false
        if (group.favorites.any { it.name == newName }) return false
        fav.name = newName
        return true
    }

    override fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean {
        moveFavoriteCalls.add(Triple(groupName, favName, newIndex))
        if (!moveFavoriteResult) return false
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        val currentIndex = group.favorites.indexOfFirst { it.name == favName }
        if (currentIndex < 0) return false
        val fav = group.favorites.removeAt(currentIndex)
        group.favorites.add(newIndex.coerceIn(0, group.favorites.size), fav)
        return true
    }

    /**
     * Mirrors `osmscout::FavoriteLocationService::MoveFavoriteToGroup`: the source
     * and destination groups and the favorite must exist, the destination name
     * collision is checked before anything is removed, a same-group destination is
     * a successful no-op, and the target index is clamped to the destination list.
     */
    override fun moveFavoriteToGroup(
        groupName: String, favName: String, targetGroupName: String, newIndex: Int
    ): Boolean {
        moveFavoriteToGroupCalls.add(
            MoveToGroupCall(
                groupName, favName, targetGroupName, newIndex, Thread.currentThread().name
            )
        )
        if (!moveFavoriteToGroupResult) return false

        val source = favGroups.firstOrNull { it.name == groupName } ?: return false
        val target = favGroups.firstOrNull { it.name == targetGroupName } ?: return false
        val fav = source.favorites.firstOrNull { it.name == favName } ?: return false

        if (source === target) return true
        if (target.favorites.any { it.name == favName }) return false

        source.favorites.remove(fav)
        target.favorites.add(newIndex.coerceIn(0, target.favorites.size), fav)
        return true
    }

    /**
     * Mirrors `osmscout::FavoriteLocationService::MoveGroup`: the group must exist
     * and the target index is clamped to the order after the group was removed, so
     * an index beyond the end lands last and a negative index lands first.
     */
    override fun moveGroup(groupName: String, newIndex: Int): Boolean {
        moveGroupCalls.add(MoveGroupCall(groupName, newIndex, Thread.currentThread().name))
        if (!moveGroupResult) return false
        val currentIndex = favGroups.indexOfFirst { it.name == groupName }
        if (currentIndex < 0) return false
        val group = favGroups.removeAt(currentIndex)
        favGroups.add(newIndex.coerceIn(0, favGroups.size), group)
        return true
    }

    override fun setStarred(groupName: String, favName: String, starred: Boolean): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        val fav = group.favorites.firstOrNull { it.name == favName } ?: return false
        fav.attributes["starred"] = starred.toString()
        return true
    }

    override fun isStarred(groupName: String, favName: String): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        val fav = group.favorites.firstOrNull { it.name == favName } ?: return false
        return fav.attributes["starred"] == "true"
    }

    override fun setGroupColor(groupName: String, color: String): Boolean {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return false
        group.attributes["color"] = color
        return true
    }

    override fun getGroupColor(groupName: String): String? {
        val group = favGroups.firstOrNull { it.name == groupName } ?: return null
        return group.attributes["color"]
    }
}
