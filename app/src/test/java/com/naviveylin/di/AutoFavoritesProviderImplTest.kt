package com.naviveylin.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.data.FavoriteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [AutoFavoritesProviderImpl] (spec: auto-favorites — favorite
 * add/remove from details screen): add persists into the shared repository,
 * remove deletes the matching favorite, state flow reflects the store, and
 * coordinate matching works across groups. Default Robolectric sandbox, no
 * @Config (per AGENTS.md classloader rule).
 */
@RunWith(RobolectricTestRunner::class)
class AutoFavoritesProviderImplTest {

    private lateinit var context: Context
    private lateinit var client: FakeOSMScoutClient
    private lateinit var repository: FavoriteRepository
    private lateinit var provider: AutoFavoritesProviderImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        client = FakeOSMScoutClient()
        repository = FavoriteRepository(client)
        provider = AutoFavoritesProviderImpl(dagger.Lazy { repository })
    }

    private suspend fun initRepository() {
        repository.init(context.filesDir.absolutePath + "/fav-provider-test.json")
    }

    @Test
    fun addFavoritePersistsIntoDefaultGroup() = runTest {
        initRepository()
        val added = provider.addFavorite("Mario's", 51.5, 7.4)
        assertTrue("add must succeed", added)
        val favorites = provider.favoriteLocations().first()
        assertEquals(
            listOf("Mario's"),
            favorites[DEFAULT_FAVORITE_GROUP]?.map { it.name }
        )
    }

    @Test
    fun removeFavoriteDeletesMatchingFavorite() = runTest {
        initRepository()
        provider.addFavorite("Mario's", 51.5, 7.4)
        val removed = provider.removeFavorite(51.5, 7.4)
        assertTrue("remove must succeed", removed)
        val favorites = provider.favoriteLocations().first()
        assertTrue("favorites must be empty after remove", favorites.values.all { it.isEmpty() })
    }

    @Test
    fun removeFavoriteReturnsFalseWhenNotFound() = runTest {
        initRepository()
        assertFalse(provider.removeFavorite(51.5, 7.4))
    }

    @Test
    fun stateFlowReflectsAddAndRemove() = runTest {
        initRepository()
        provider.addFavorite("A", 1.0, 2.0)
        assertTrue(provider.favoriteLocations().first().values.flatten().isNotEmpty())
        provider.removeFavorite(1.0, 2.0)
        assertTrue(provider.favoriteLocations().first().values.flatten().isEmpty())
    }

    @Test
    fun findFavoriteByCoordinatesAcrossGroups() {
        val favorites = mapOf(
            "Work" to listOf(FavoriteLocation("Office", 1.0, 2.0)),
            "Home" to listOf(FavoriteLocation("Home", 3.0, 4.0))
        )
        assertEquals("Home" to "Home", findFavoriteByCoordinates(favorites, 3.0, 4.0))
        assertEquals("Work" to "Office", findFavoriteByCoordinates(favorites, 1.0, 2.0))
        assertNull(findFavoriteByCoordinates(favorites, 9.0, 9.0))
    }

    // --- The store's readiness (spec auto-favorites — The favourites list distinguishes a loading
    // store from an empty one; design D2) ---

    @Test
    fun storeIsNotReadyBeforeItsInitialisation() = runTest {
        // The favorites flow is an empty map from construction, so a surface cannot tell "not
        // initialised" from "initialised and empty" without this signal.
        assertFalse("an uninitialised store must not report readiness", provider.storeReady().value)
    }

    @Test
    fun storeReportsReadyAfterASuccessfulInitialisation() = runTest {
        initRepository()

        assertTrue("a finished initialisation reports readiness", provider.storeReady().value)
    }

    /**
     * The failing path: the attempt ends even when the store cannot be read, because a permanent
     * loading state would hide the failure behind a promise that never resolves. A repository without a
     * client stands in for the failure — the fake client's load path always succeeds and never throws.
     */
    @Test
    fun storeReportsReadyAfterAFailedInitialisation() = runTest {
        val broken = FavoriteRepository()
        val brokenProvider = AutoFavoritesProviderImpl(dagger.Lazy { broken })

        val attempt = runCatching { broken.init(context.filesDir.absolutePath + "/fav-broken.json") }

        assertTrue("the initialisation has to fail for this case to mean anything", attempt.isFailure)
        assertTrue("a failed attempt still reports readiness", brokenProvider.storeReady().value)
    }
}
