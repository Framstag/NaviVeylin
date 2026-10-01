package com.naviveylin.di

import android.util.Log
import com.framstag.libosmscout.client.OSMScoutClient
import com.naviveylin.core.search.RegionFixReference
import com.naviveylin.core.search.SearchRegionScope
import com.naviveylin.core.search.resolveRegionScope
import com.naviveylin.location.LocationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Supplies the admin-region handle the car search is scoped with
 * (spec: `auto-search` — Search scoped by the car position's admin region).
 *
 * [NONE] is the no-scoping source: it is what a caller without a car position
 * source (host tests) passes, and it reproduces the pre-change behaviour of an
 * unconstrained search.
 */
fun interface CarSearchRegionScope {

    /** Backend region handle for the next car search, or [SearchRegionScope.NO_HANDLE]. */
    fun handleForSearch(): Long

    companion object {
        /** No region scoping — the search runs unconstrained. */
        val NONE = CarSearchRegionScope { SearchRegionScope.NO_HANDLE }
    }
}

/**
 * Owns the Android Auto search's admin-region scope between searches
 * (spec: `auto-search` — Car region scope follows the car's movement).
 *
 * The decision itself is the shared rule ([resolveRegionScope]) the phone search
 * panel uses, so the car cannot drift from the phone (spec: `auto-search` — Car
 * and phone region scoping parity); this class only holds the scope, reads the
 * car's position source and performs the backend calls.
 *
 * Threading: called from the search's background block
 * (`guidelines/Design.md` §4 — no native work on the car-app main thread), and
 * it resolves the native client lazily on that thread — never while Hilt
 * resolves the graph.
 *
 * Diagnostics carry identity (handle, region name), never a position
 * (spec: `auto-diagnostics`).
 */
@Singleton
class CarSearchRegionSource @Inject constructor(
    private val client: Provider<OSMScoutClient>,
    private val locationService: LocationService
) : CarSearchRegionScope {

    private var scope = SearchRegionScope()

    override fun handleForSearch(): Long {
        val fix = locationService.location.value?.let {
            RegionFixReference(lat = it.lat, lon = it.lon, accuracyMeters = it.accuracy)
        }
        val outcome = resolveRegionScope(
            fix = fix,
            previous = scope,
            releaseRegion = { handle ->
                try {
                    client.get().releaseAdminRegion(handle)
                } catch (e: Exception) {
                    Log.e(TAG, "releaseAdminRegion failed", e)
                }
            },
            resolveRegion = { lat, lon ->
                try {
                    val handle = client.get().resolveAdminRegion(lat, lon)
                    Log.d(TAG, "resolveAdminRegion -> handle=$handle")
                    handle
                } catch (e: Exception) {
                    Log.e(TAG, "resolveAdminRegion failed", e)
                    SearchRegionScope.NO_HANDLE
                }
            },
            scopeNameOf = { handle ->
                try {
                    client.get().getAdminRegionScopeName(handle)
                } catch (e: Exception) {
                    Log.e(TAG, "getAdminRegionScopeName failed", e)
                    null
                }
            },
            regionNameOf = { handle ->
                try {
                    client.get().getAdminRegionName(handle)
                } catch (e: Exception) {
                    Log.e(TAG, "getAdminRegionName failed", e)
                    null
                }
            }
        )
        scope = outcome.scope
        Log.d(
            TAG,
            "search scope ${outcome.decision}: handle=${scope.handle} region=${scope.name}"
        )
        return scope.handle
    }

    private companion object {
        const val TAG = "CarSearchRegion"
    }
}

/** Binds the car search's region-scope source for the search provider. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CarSearchRegionModule {

    @Binds
    abstract fun bindCarSearchRegionScope(source: CarSearchRegionSource): CarSearchRegionScope
}
