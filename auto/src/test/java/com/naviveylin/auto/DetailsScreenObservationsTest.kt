package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.AutoLocationProvider
import com.naviveylin.core.AutoPosition
import com.naviveylin.core.BasemapReloadNotifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [DetailsScreenObservations] (spec: auto/screen-observation — One instance of each
 * observation per started period; A stopped screen performs no renderer or host work; Observations
 * are re-established with the current state on start; design D2/D4/D5/D8).
 *
 * The screen itself needs a live `CarContext` and a Hilt entry point, so its observation wiring is
 * covered here through the seam: the real [CarScreenObservations] plus mockable `:core` providers and
 * counting effects instead of the renderer gate. No `CarContext`.
 *
 * Robolectric is the runner because the fault case asserts the confined fault's diagnostics entry and
 * the shared handler logs through `android.util.Log` (same reason [CarScreenObservationsTest] uses
 * it). Nothing here touches `OSMScoutClient`, so no JNI stub is involved.
 *
 * All "once" assertions are deltas against a counter taken before the emission — a `StateFlow`
 * re-emits its current value to each new collector by design (see [CarScreenObservationsTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailsScreenObservationsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Location provider backed by a state flow; nothing else is exercised here. */
    private class FakeLocationProvider : AutoLocationProvider {
        val flow = MutableStateFlow<AutoPosition?>(null)
        override fun position(): StateFlow<AutoPosition?> = flow.asStateFlow()
        override fun start() = Unit
        override fun stop() = Unit
    }

    /** Favorites provider backed by a state flow. */
    private class FakeFavoritesProvider : AutoFavoritesProvider {
        val flow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())
        val order = MutableStateFlow<List<String>>(emptyList())
        val starred = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())
        override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> = flow.asStateFlow()
        override fun groupOrder(): StateFlow<List<String>> = order.asStateFlow()
        override fun starredOrder(): StateFlow<List<StarredFavoriteLocation>> = starred.asStateFlow()
        override suspend fun init(filePath: String): Boolean = false
        override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean = false
        override suspend fun removeFavorite(lat: Double, lon: Double): Boolean = false
    }

    private val location = FakeLocationProvider()
    private val favorites = FakeFavoritesProvider()
    private val basemap = BasemapReloadNotifier()

    private val fixes = mutableListOf<AutoPosition>()
    private val favoriteSets = mutableListOf<Map<String, List<FavoriteLocation>>>()

    /** Basemap re-render requests; the screen's callback is `rendererGate.invalidateData()`. */
    private var basemapInvalidations = 0

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(observations: CarScreenObservations) = DetailsScreenObservations(
        observations = observations,
        locationProvider = location,
        favoritesProvider = favorites,
        basemapNotifier = basemap,
        onFix = { fixes += it },
        onFavorites = { favoriteSets += it },
        onBasemapRevision = { basemapInvalidations++ }
    )

    private val fix = AutoPosition(lat = 51.5, lon = 7.5, bearing = 90.0, accuracy = 5.0, speedKmH = 40.0)

    private fun favorite(name: String) = mapOf("Default" to listOf(FavoriteLocation(name, 51.5, 7.5)))

    @Test
    fun everyObservedSourceReachesItsEffect() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals("the details screen observes three sources", 3, observations.liveObservationCount)

        val favoritesBefore = favoriteSets.size
        location.flow.value = fix
        favorites.flow.value = favorite("Home")
        basemap.bump()
        advanceUntilIdle()

        assertEquals("the GPS feed reaches the preview", listOf(fix), fixes)
        assertEquals(
            "the new favorite set reaches the screen exactly once (the empty map was the period's initial value)",
            1,
            favoriteSets.size - favoritesBefore
        )
        assertEquals("Home", favoriteSets.last().values.flatten().single().name)
        assertEquals("a basemap revision forces a re-render", 1, basemapInvalidations)
    }

    @Test
    fun tenStartStopCyclesLeaveOneInstancePerSource() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "Details screen stopped and started repeatedly". The host
        // stops and starts a screen on every background round trip and on every push/pop of another
        // screen, so a per-collector job list would accumulate copies.
        val observations = observations()
        val wiring = wiring(observations)

        repeat(10) { cycle ->
            wiring.start()
            advanceUntilIdle()
            assertEquals("three observations in cycle ${cycle + 1}", 3, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${cycle + 1}", 0, observations.liveObservationCount)
        }

        wiring.start()
        advanceUntilIdle()
        assertEquals("a restarted screen still observes three sources", 3, observations.liveObservationCount)

        val fixesBefore = fixes.size
        location.flow.value = fix
        advanceUntilIdle()
        assertEquals("one fix reaches one observation", 1, fixes.size - fixesBefore)
    }

    @Test
    fun aStoppedScreenObservesNothing() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "A fix arrives while the details screen is stopped",
        // "Favorites change while the details screen is stopped", "Basemap data changes while the
        // details screen is stopped". This is the defect TODO.md §57 records: the collectors used to
        // run from `init` and kept sending renderer work while the screen was covered or backgrounded.
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()

        location.flow.value = fix
        advanceUntilIdle()
        assertEquals(1, fixes.size)

        observations.stop()
        val fixesBefore = fixes.size
        val favoriteSetsBefore = favoriteSets.size
        val basemapBefore = basemapInvalidations

        location.flow.value = AutoPosition(lat = 51.6, lon = 7.6, bearing = 0.0, accuracy = 5.0, speedKmH = 10.0)
        favorites.flow.value = favorite("Work")
        basemap.bump()
        advanceUntilIdle()

        assertEquals("a stopped screen must not draw a marker or move the viewport", fixesBefore, fixes.size)
        assertEquals("a stopped screen must not send a favorite set", favoriteSetsBefore, favoriteSets.size)
        assertEquals("a stopped screen must not request a render", basemapBefore, basemapInvalidations)
    }

    @Test
    fun aChangeWhileStoppedIsAppliedOnceOnStart() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — "Details preview restores the current state on start": a
        // change made while stopped is not lost and is applied exactly once.
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()
        observations.stop()

        favorites.flow.value = favorite("Work")
        basemap.bump()

        val favoriteSetsBefore = favoriteSets.size
        val basemapBefore = basemapInvalidations
        wiring.start()
        advanceUntilIdle()

        assertEquals("the current favorite set is applied exactly once", 1, favoriteSets.size - favoriteSetsBefore)
        assertEquals("Work", favoriteSets.last().values.flatten().single().name)
        assertEquals("the current basemap revision is applied exactly once", 1, basemapInvalidations - basemapBefore)
    }

    @Test
    fun theInitialBasemapRevisionIsNotApplied() = runTest(mainDispatcher.dispatcher) {
        // Revision 0 is the "nothing changed yet" value: starting the screen must not force a
        // re-render (spec: basemap-loading — re-render on change only).
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals(0, basemapInvalidations)
    }

    @Test
    fun aNullFixIsNotApplied() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        location.flow.value = null
        advanceUntilIdle()

        assertEquals("no fix yet is not a position", 0, fixes.size)
    }

    @Test
    fun aThrowingEffectIsConfinedAndSiblingsKeepRunning() = runTest(mainDispatcher.dispatcher) {
        // Spec: auto/screen-observation — An observation fault is confined to its observation: the
        // failure is logged with the observation's key, that observation ends, the screen's other
        // observations keep running, and the process survives (TODO.md §51: an app process that dies
        // during a car session takes the templates host down with it).
        val file = java.io.File.createTempFile("diag", ".log")
        com.naviveylin.core.DiagnosticsLog.initForTest(file)
        try {
            val observations = observations()
            val wiring = DetailsScreenObservations(
                observations = observations,
                locationProvider = location,
                favoritesProvider = favorites,
                basemapNotifier = basemap,
                onFix = { fixes += it },
                onFavorites = { throw IllegalStateException("boom") },
                onBasemapRevision = { basemapInvalidations++ }
            )
            wiring.start()
            advanceUntilIdle()

            assertEquals("the faulting observation ended, its siblings survived", 2, observations.liveObservationCount)
            assertTrue(
                "the fault is logged with the observation's key",
                com.naviveylin.core.DiagnosticsLog.readEntries().any { it.contains("observation 'favorites' failed") }
            )

            val fixesBefore = fixes.size
            location.flow.value = fix
            advanceUntilIdle()
            assertEquals("the position observation still delivers", 1, fixes.size - fixesBefore)
        } finally {
            com.naviveylin.core.DiagnosticsLog.reset()
            file.delete()
        }
    }
}
