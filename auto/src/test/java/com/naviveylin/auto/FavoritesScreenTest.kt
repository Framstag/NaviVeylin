package com.naviveylin.auto

import android.content.Context
import android.os.Looper
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.core.AutoEntryPoint
import com.naviveylin.core.AutoFavoritesProvider
import com.naviveylin.core.NavigationViewModel
import dagger.hilt.EntryPoints
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Template tests for [FavoritesScreen] (spec: auto-favorites — favorites
 * appear without re-entering the screen).
 *
 * The screen collects the favorites flow reactively: it shows "Loading" until
 * the first emission, then renders the list (or the empty state) and updates in
 * place when the store finishes loading after the screen is already open. The
 * collect is cancelled when the screen is destroyed so collectors do not
 * accumulate across open/close cycles.
 *
 * The screen resolves its providers via `EntryPointAccessors`, which delegates
 * to `EntryPoints.get(applicationContext, ...)` — stub the real application
 * context and intercept the Java static delegate (the Kotlin object's
 * `@JvmStatic` method cannot be mocked reliably, see ki_processing_failures.log).
 */
@RunWith(RobolectricTestRunner::class)
class FavoritesScreenTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val carContext = testCarContext()
    private val navigationViewModel = mockk<NavigationViewModel>()
    private val entryPoint = mockk<AutoEntryPoint>()
    private val favoritesProvider = FakeFavoritesProvider()

    @Before
    fun setUp() {
        every { carContext.getCarService(ScreenManager::class.java) } returns mockk(relaxed = true)
        every { carContext.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        mockkStatic(EntryPoints::class)
        every { EntryPoints.get(any(), AutoEntryPoint::class.java) } returns entryPoint
        every { entryPoint.autoFavoritesProvider() } returns favoritesProvider
    }

    private fun newScreen(starredOnly: Boolean = false) =
        FavoritesScreen(carContext, navigationViewModel, starredOnly)

    private fun singleTitles(template: ListTemplate): List<String> =
        template.singleList!!.items.map { (it as Row).title.toString() }

    private fun sectionTitles(template: ListTemplate): List<String> =
        template.sectionedLists.flatMap { section ->
            section.itemList.items.map { (it as Row).title.toString() }
        }

    private fun fav(name: String) = FavoriteLocation(name, 51.5136, 7.4653)

    @Test
    fun showsLoadingBeforeFirstEmission() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        // The collect has not run yet (StandardTestDispatcher): the first
        // template is the loading row.
        assertEquals(listOf("Loading..."), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun emptyStoreShowsNoFavoritesSaved() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        advanceUntilIdle()
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))
    }

    @Test
    fun lateEmissionUpdatesListWithoutReentering() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        advanceUntilIdle() // collect runs, store still empty
        assertEquals(listOf("No favorites saved"), singleTitles(screen.onGetTemplate()))

        // The store finishes loading while the screen is already open.
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        assertEquals(listOf("Home", "Work"), sectionTitles(screen.onGetTemplate()))
    }

    @Test
    fun scopeCancelledOnDestroy() = runTest(mainDispatcherRule.dispatcher) {
        val screen = newScreen()
        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home")))
        advanceUntilIdle()
        assertEquals(listOf("Home"), sectionTitles(screen.onGetTemplate()))

        // Destroy the screen via the real lifecycle path: the collect must
        // stop. The registry starts at INITIALIZED, so drive it up first, then
        // down to DESTROYED (dispatchLifecycleEvent runs synchronously on the
        // Robolectric main thread via ThreadUtils.runOnMain).
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_CREATE)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_START)
        screen.dispatchLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()

        favoritesProvider.flow.value = mapOf("Favorites" to listOf(fav("Home"), fav("Work")))
        advanceUntilIdle()

        // Still the pre-destroy data: the cancelled collect did not re-render.
        assertEquals(listOf("Home"), sectionTitles(screen.onGetTemplate()))
    }

    /** In-memory [AutoFavoritesProvider] backed by a [MutableStateFlow]. */
    private class FakeFavoritesProvider : AutoFavoritesProvider {
        val flow = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())

        override fun favoriteLocations() = flow
        override suspend fun init(filePath: String) = true
        override suspend fun addFavorite(name: String, lat: Double, lon: Double) = true
        override suspend fun removeFavorite(lat: Double, lon: Double) = true
    }
}
