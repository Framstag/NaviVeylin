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
        provider = AutoFavoritesProviderImpl(repository)
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
}
