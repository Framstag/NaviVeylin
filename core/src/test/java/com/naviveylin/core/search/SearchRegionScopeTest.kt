package com.naviveylin.core.search

import com.naviveylin.core.haversineDistanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared admin-region scoping rule
 * ([resolveRegionScope]), which the phone search panel and the car search both
 * use (spec: `location-search` — Search scoped by current admin region, Admin
 * region follows user movement; spec: `auto-search` — Search scoped by the car
 * position's admin region, Car region scope follows the car's movement).
 */
class SearchRegionScopeTest {

    /** Rough meters per degree of latitude, for placing test fixes at a known distance. */
    private fun latOffset(meters: Double): Double = meters / 111_194.93

    private class FakeBackend(
        var nextHandle: Long = 7L,
        var scopeName: String? = "Regierungsbezirk Arnsberg",
        var regionName: String? = "Dortmund"
    ) {
        val resolveCalls = mutableListOf<Pair<Double, Double>>()
        val releasedHandles = mutableListOf<Long>()
        val nameLookups = mutableListOf<Long>()

        fun resolve(lat: Double, lon: Double): Long {
            resolveCalls.add(lat to lon)
            return nextHandle
        }

        fun release(handle: Long) {
            releasedHandles.add(handle)
        }

        fun scopeNameOf(handle: Long): String? {
            nameLookups.add(handle)
            return scopeName
        }

        fun regionNameOf(handle: Long): String? = regionName
    }

    private fun resolveWith(
        backend: FakeBackend,
        fix: RegionFixReference?,
        previous: SearchRegionScope = SearchRegionScope(),
        maxAccuracyMeters: Double = DEFAULT_MAX_ACCURACY_METERS,
        movementThresholdMeters: Double = DEFAULT_MOVEMENT_THRESHOLD_METERS
    ) = resolveRegionScope(
        fix = fix,
        previous = previous,
        releaseRegion = backend::release,
        resolveRegion = backend::resolve,
        scopeNameOf = backend::scopeNameOf,
        regionNameOf = backend::regionNameOf,
        maxAccuracyMeters = maxAccuracyMeters,
        movementThresholdMeters = movementThresholdMeters
    )

    private fun fix(lat: Double = 51.5136, lon: Double = 7.4653, accuracy: Double = 10.0) =
        RegionFixReference(lat = lat, lon = lon, accuracyMeters = accuracy)

    @Test
    fun `usable fix resolves the region and keeps its position`() {
        val backend = FakeBackend()
        val outcome = resolveWith(backend, fix())

        assertEquals(RegionScopeDecision.RESOLVED, outcome.decision)
        assertEquals(7L, outcome.scope.handle)
        assertEquals(51.5136, outcome.scope.lat, 1e-9)
        assertEquals(7.4653, outcome.scope.lon, 1e-9)
        assertEquals(1, backend.resolveCalls.size)
    }

    @Test
    fun `scope region name is preferred over the resolved region name`() {
        val backend = FakeBackend(scopeName = "Regierungsbezirk Arnsberg", regionName = "Dortmund")
        assertEquals("Regierungsbezirk Arnsberg", resolveWith(backend, fix()).scope.name)
    }

    @Test
    fun `region name is the fallback when the scope name is unknown`() {
        val backend = FakeBackend(scopeName = null, regionName = "Dortmund")
        assertEquals("Dortmund", resolveWith(backend, fix()).scope.name)
    }

    @Test
    fun `no name is carried when both lookups fail`() {
        val backend = FakeBackend(scopeName = null, regionName = null)
        assertNull(resolveWith(backend, fix()).scope.name)
    }

    @Test
    fun `no fix runs the search unconstrained`() {
        val backend = FakeBackend()
        val outcome = resolveWith(backend, fix = null)

        assertEquals(RegionScopeDecision.FIX_UNUSABLE, outcome.decision)
        assertEquals(SearchRegionScope(), outcome.scope)
        assertTrue(backend.resolveCalls.isEmpty())
    }

    @Test
    fun `a coarse fix never scopes the search`() {
        val backend = FakeBackend()
        val previous = SearchRegionScope(handle = 7L, lat = 51.5136, lon = 7.4653, name = "Dortmund")
        val outcome = resolveWith(backend, fix(accuracy = 80.0), previous = previous)

        assertEquals(RegionScopeDecision.FIX_UNUSABLE, outcome.decision)
        assertEquals(SearchRegionScope.NO_HANDLE, outcome.scope.handle)
        // The region that no longer applies is released, and nothing is resolved.
        assertEquals(listOf(7L), backend.releasedHandles)
        assertTrue(backend.resolveCalls.isEmpty())
    }

    @Test
    fun `the accuracy gate is inclusive`() {
        val backend = FakeBackend()
        assertEquals(RegionScopeDecision.RESOLVED, resolveWith(backend, fix(accuracy = 50.0)).decision)
    }

    @Test
    fun `a non-finite position is unusable`() {
        val backend = FakeBackend()
        assertEquals(
            RegionScopeDecision.FIX_UNUSABLE,
            resolveWith(backend, fix(lat = Double.NaN)).decision
        )
        assertEquals(
            RegionScopeDecision.FIX_UNUSABLE,
            resolveWith(backend, fix(lon = Double.POSITIVE_INFINITY)).decision
        )
        assertTrue(backend.resolveCalls.isEmpty())
    }

    @Test
    fun `a region is reused while the position stays within the movement threshold`() {
        val backend = FakeBackend()
        val first = resolveWith(backend, fix())
        val moved = fix(lat = 51.5136 + latOffset(499.0))

        val outcome = resolveWith(backend, moved, previous = first.scope)

        assertEquals(RegionScopeDecision.REUSED, outcome.decision)
        assertEquals(first.scope, outcome.scope)
        // One resolution for the first fix only — no backend call for the reuse.
        assertEquals(1, backend.resolveCalls.size)
        assertTrue(backend.releasedHandles.isEmpty())
    }

    @Test
    fun `movement beyond the threshold releases the old region and resolves a new one`() {
        val backend = FakeBackend()
        val first = resolveWith(backend, fix())
        backend.nextHandle = 8L
        backend.regionName = "Essen"
        backend.scopeName = null

        val outcome = resolveWith(backend, fix(lat = 51.5136 + latOffset(1100.0)), previous = first.scope)

        assertEquals(RegionScopeDecision.RESOLVED, outcome.decision)
        assertEquals(8L, outcome.scope.handle)
        assertEquals("Essen", outcome.scope.name)
        assertEquals(listOf(7L), backend.releasedHandles)
        assertEquals(2, backend.resolveCalls.size)
        // The old handle is released before the new region is resolved.
        assertEquals(51.5136 + latOffset(1100.0), backend.resolveCalls.last().first, 1e-6)
    }

    @Test
    fun `the movement threshold is inclusive`() {
        val backend = FakeBackend()
        val previous = SearchRegionScope(handle = 7L, lat = 51.5136, lon = 7.4653, name = "Dortmund")
        val moved = fix(lat = 51.5136 + latOffset(500.0))
        // The rule compares against the real haversine distance, so the threshold
        // is pinned to it exactly instead of to a rounded meter value.
        val distance = haversineDistanceMeters(previous.lat, previous.lon, moved.lat, moved.lon)

        val atThreshold = resolveWith(
            backend,
            moved,
            previous = previous,
            movementThresholdMeters = distance
        )
        assertEquals(RegionScopeDecision.REUSED, atThreshold.decision)

        val beyondThreshold = resolveWith(
            backend,
            moved,
            previous = previous,
            movementThresholdMeters = distance - 0.5
        )
        assertEquals(RegionScopeDecision.RESOLVED, beyondThreshold.decision)
        assertEquals(listOf(7L), backend.releasedHandles)
    }

    @Test
    fun `a failed resolution leaves the search unconstrained`() {
        val backend = FakeBackend(nextHandle = 0L)
        val previous = SearchRegionScope(handle = 7L, lat = 51.5136, lon = 7.4653, name = "Dortmund")

        val outcome = resolveWith(backend, fix(lat = 51.52, lon = 7.4653), previous = previous)

        assertEquals(RegionScopeDecision.RESOLVE_FAILED, outcome.decision)
        assertEquals(SearchRegionScope.NO_HANDLE, outcome.scope.handle)
        assertNull(outcome.scope.name)
        assertEquals(listOf(7L), backend.releasedHandles)
    }

    @Test
    fun `no release is issued when no region was held`() {
        val backend = FakeBackend(nextHandle = 0L)
        resolveWith(backend, fix())
        assertTrue(backend.releasedHandles.isEmpty())

        val unusable = resolveWith(backend, fix = null, previous = SearchRegionScope())
        assertEquals(RegionScopeDecision.FIX_UNUSABLE, unusable.decision)
        assertTrue(backend.releasedHandles.isEmpty())
    }

    @Test
    fun `the gates are parameters, so a surface can scope more loosely`() {
        val backend = FakeBackend()
        val outcome = resolveWith(
            backend,
            fix(accuracy = 250.0),
            maxAccuracyMeters = 500.0,
            movementThresholdMeters = 50.0
        )
        assertEquals(RegionScopeDecision.RESOLVED, outcome.decision)
        assertEquals(7L, outcome.scope.handle)
    }
}
