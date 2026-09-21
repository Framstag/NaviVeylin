package com.naviveylin.auto

import com.framstag.libosmscout.client.FavoriteLocation
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
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [MapScreenObservations] (spec: auto/screen-observation — One instance of
 * each observation per started period; A stopped screen performs no renderer or host
 * work; Observations are re-established with the current state on start; design D2).
 *
 * The screen object itself needs a live `CarContext` and a Hilt entry point
 * (`MapScreenTest`), so the screen's observation wiring is covered here through its
 * seam: the real [CarScreenObservations] plus mockable `:core` providers and counting
 * effects. No `CarContext`, no Robolectric.
 *
 * All "once" assertions are deltas against a counter taken before the emission — a
 * `StateFlow` re-emits its current value to each new collector by design (see
 * [CarScreenObservationsTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenObservationsTest {

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
        override fun favoriteLocations(): StateFlow<Map<String, List<FavoriteLocation>>> = flow.asStateFlow()
        override suspend fun init(filePath: String): Boolean = false
        override suspend fun addFavorite(name: String, lat: Double, lon: Double): Boolean = false
        override suspend fun removeFavorite(lat: Double, lon: Double): Boolean = false
    }

    private val location = FakeLocationProvider()
    private val favorites = FakeFavoritesProvider()
    private val basemap = BasemapReloadNotifier()
    private val dark = MutableStateFlow(false)

    private val fixes = mutableListOf<AutoPosition>()
    private val favoriteSets = mutableListOf<List<FavoriteLocation>>()
    private val darkValues = mutableListOf<Boolean>()
    private var basemapInvalidations = 0

    private fun observations() = CarScreenObservations(mainDispatcher.dispatcher)

    private fun wiring(observations: CarScreenObservations) = MapScreenObservations(
        observations = observations,
        locationProvider = location,
        favoritesProvider = favorites,
        basemapNotifier = basemap,
        resolvedDark = dark,
        onFix = { fixes += it },
        onFavorites = { favoriteSets += it },
        onDark = { darkValues += it },
        onBasemapRevision = { basemapInvalidations++ }
    )

    private val fix = AutoPosition(lat = 51.5, lon = 7.5, bearing = 90.0, accuracy = 5.0, speedKmH = 40.0)

    @Test
    fun everyObservedSourceReachesItsEffect() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        wiring(observations).start()
        advanceUntilIdle()

        assertEquals(4, observations.liveObservationCount)

        val favoritesBefore = favoriteSets.size
        val darkBefore = darkValues.size
        location.flow.value = fix
        favorites.flow.value = mapOf("Default" to listOf(FavoriteLocation("Home", 51.5, 7.5)))
        dark.value = true
        basemap.bump()
        advanceUntilIdle()

        assertEquals("the GPS feed is observed", listOf(fix), fixes)
        assertEquals("favorites reach the renderer as one flat list", 1, favoriteSets.size - favoritesBefore)
        assertEquals(1, favoriteSets.last().size)
        assertEquals("the resolved presentation is observed", 1, darkValues.size - darkBefore)
        assertEquals(true, darkValues.last())
        assertEquals("a basemap revision forces a re-render", 1, basemapInvalidations)
    }

    @Test
    fun threeStartStopCyclesLeaveOneInstancePerSource() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)

        repeat(3) { cycle ->
            wiring.start()
            advanceUntilIdle()
            assertEquals("four observations in cycle ${cycle + 1}", 4, observations.liveObservationCount)

            observations.stop()
            assertEquals("stopped after cycle ${cycle + 1}", 0, observations.liveObservationCount)
        }

        wiring.start()
        advanceUntilIdle()
        assertEquals("a restarted screen still observes four sources", 4, observations.liveObservationCount)

        val fixesBefore = fixes.size
        location.flow.value = fix
        advanceUntilIdle()
        assertEquals("one fix reaches one observation", 1, fixes.size - fixesBefore)
    }

    @Test
    fun aStoppedScreenObservesNothing() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()

        location.flow.value = fix
        advanceUntilIdle()
        assertEquals(1, fixes.size)

        observations.stop()
        val favoritesBefore = favoriteSets.size
        favorites.flow.value = mapOf("Default" to listOf(FavoriteLocation("Home", 51.5, 7.5)))
        basemap.bump()
        advanceUntilIdle()

        assertEquals(
            "a stopped screen must not touch the renderer or the host",
            favoritesBefore,
            favoriteSets.size
        )
        assertEquals(0, basemapInvalidations)
    }

    @Test
    fun aChangeWhileStoppedIsAppliedOnceOnStart() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)
        wiring.start()
        advanceUntilIdle()
        observations.stop()

        // Favorites change while the screen is stopped.
        favorites.flow.value = mapOf("Default" to listOf(FavoriteLocation("Work", 51.6, 7.6)))

        val before = favoriteSets.size
        wiring.start()
        advanceUntilIdle()

        assertEquals("the change is applied exactly once on return", 1, favoriteSets.size - before)
        assertEquals("Work", favoriteSets.last().single().name)
    }

    @Test
    fun theInitialBasemapRevisionIsNotApplied() = runTest(mainDispatcher.dispatcher) {
        val observations = observations()
        val wiring = wiring(observations)

        // Revision 0 is the "nothing changed yet" value: starting the screen must not
        // force a re-render (spec: basemap-loading — re-render on change only).
        wiring.start()
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
}
