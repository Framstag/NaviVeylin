package com.framstag.libosmscout.client

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Test double for [OSMScoutClient] that records style flag pushes instead of
 * calling into native code. Lives in the client package so it can access the
 * package-private constructor.
 */
class FakeOSMScoutClient : OSMScoutClient() {

    /** Recorded (key, value) style flag pushes in call order. */
    val styleFlags: MutableList<Pair<String, Boolean>> = CopyOnWriteArrayList()

    /** Number of [render] invocations (full direct native renders). */
    val renderCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** Number of [renderWithRouteAndPois] invocations (per-tile renders + overlay renders). */
    val renderWithRouteAndPoisCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** Optional artificial delay (ms) inside [renderWithRouteAndPois] — used to
     *  interleave mode switches with an in-flight tile render in tests. */
    var renderWithRouteAndPoisDelayMs: Long = 0L

    /** Search-selection marker latitude from the last [renderWithRouteAndPois] (NaN when unset). */
    var lastSearchSelLat: Double = Double.NaN

    /** Search-selection marker longitude from the last [renderWithRouteAndPois] (NaN when unset). */
    var lastSearchSelLon: Double = Double.NaN

    /** Magnification of the last render (either entry point; -1 until first render). */
    @Volatile
    var lastRenderMag: Int = -1

    /** Latitude of the last render (NaN until first render). */
    @Volatile
    var lastRenderLat: Double = Double.NaN

    /** Longitude of the last render (NaN until first render). */
    @Volatile
    var lastRenderLon: Double = Double.NaN

    override fun setStyleSheetFlag(key: String, value: Boolean) {
        styleFlags.add(key to value)
    }

    override fun render(
        width: Int, height: Int,
        lat: Double, lon: Double,
        angle: Double, magnification: Int
    ): IntArray? {
        renderCount.incrementAndGet()
        lastRenderLat = lat
        lastRenderLon = lon
        lastRenderMag = magnification
        return createTestPixels(width, height)
    }

    override fun renderWithRouteAndPois(
        width: Int, height: Int,
        lat: Double, lon: Double, angle: Double, magnification: Int,
        routeLats: DoubleArray?, routeLons: DoubleArray?,
        favoriteLats: DoubleArray?, favoriteLons: DoubleArray?,
        searchSelLat: Double, searchSelLon: Double,
        trackLats: DoubleArray?, trackLons: DoubleArray?
    ): IntArray? {
        renderWithRouteAndPoisCount.incrementAndGet()
        lastSearchSelLat = searchSelLat
        lastSearchSelLon = searchSelLon
        lastRenderLat = lat
        lastRenderLon = lon
        lastRenderMag = magnification
        if (renderWithRouteAndPoisDelayMs > 0L) {
            Thread.sleep(renderWithRouteAndPoisDelayMs)
        }
        return createTestPixels(width, height)
    }

    private fun createTestPixels(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        val fillColor = android.graphics.Color.rgb(200, 220, 240)
        pixels.fill(fillColor)
        return pixels
    }

    // --- Route calculation / navigation stubs (used by car-only route fallback tests) ---

    /** Route delivered by [calculateRouteWithProfile]; null → [deliverRouteError]. */
    var routeToDeliver: RouteEntry? = null

    /** Error text delivered when [routeToDeliver] is null. */
    var deliverRouteError: String? = null

    /** Number of [calculateRouteWithProfile] invocations. */
    var routeCalculationCount = 0

    override fun calculateRouteWithProfile(
        startLat: Double, startLon: Double,
        destLat: Double, destLon: Double,
        profile: RoutingProfile,
        callback: RouteCallback
    ) {
        routeCalculationCount++
        val route = routeToDeliver
        if (route != null) {
            callback.onSuccess(route)
        } else {
            callback.onError(deliverRouteError ?: "No route available")
        }
    }

    override fun startNavigationWithVehicle(
        routeHandle: Long,
        vehicle: Vehicle,
        listener: NavigationListener
    ): NavigationController? {
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

    /** Handles passed to [searchLocations] in call order. */
    val searchAdminRegionHandles = mutableListOf<Long>()

    /** Queries passed to [searchLocations] in call order. */
    val searchQueries = mutableListOf<String>()

    /** Limits passed to [searchLocations] in call order. */
    val searchLimits = mutableListOf<Int>()

    /** Results returned by the next [searchLocations] call (default: empty). */
    var nextSearchResults: Array<LocationEntry>? = emptyArray()

    override fun searchLocations(query: String, limit: Int, adminRegionHandle: Long): Array<LocationEntry>? {
        searchAdminRegionHandles.add(adminRegionHandle)
        searchQueries.add(query)
        searchLimits.add(limit)
        return nextSearchResults
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

    override fun getObjectBoundingBox(
        lat: Double, lon: Double, magnification: Int
    ): DoubleArray? {
        return null
    }

    // --- In-memory favorites CRUD (mirrors C++ FavoriteLocationService) ---

    private val favGroups = mutableListOf<FavoriteLocationGroup>()

    override fun loadFavoriteLocations(filePath: String): Boolean {
        favGroups.clear()
        return true
    }

    override fun saveFavoriteLocations(
        filePath: String,
        groups: Array<out FavoriteLocationGroup>
    ): Boolean = true

    override fun getFavoriteGroups(): Array<FavoriteLocationGroup> =
        favGroups.toTypedArray()

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
