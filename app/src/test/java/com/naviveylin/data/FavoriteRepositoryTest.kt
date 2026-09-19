package com.naviveylin.data

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies FavoriteRepository group/favorite CRUD, including the
 * auto-create-group-on-add behavior for the details-sheet "+ New group" flow.
 *
 * Runs under Robolectric (like all other tests that exercise the native
 * OSMScoutClient fake): a plain-JUnit class that triggers the native stub
 * library load would collide with the Robolectric sandbox classloader when
 * the full suite runs in one JVM.
 */
@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryTest {

    private suspend fun newRepository(): Pair<FavoriteRepository, FakeOSMScoutClient> {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)
        repo.init("/tmp/favorites-test.json")
        return repo to client
    }

    @Test
    fun addFavoriteCreatesMissingGroup() = runTest {
        val (repo, _) = newRepository()

        assertTrue(repo.addFavorite("NewGroup", "My Place", 51.5, 7.4))

        val groups = repo.favorites.value
        assertEquals(listOf("NewGroup"), groups.keys.toList())
        assertEquals(1, groups["NewGroup"]?.size)
        assertEquals("My Place", groups["NewGroup"]?.first()?.name)
        assertEquals(51.5, groups["NewGroup"]?.first()?.lat ?: 0.0, 1e-9)
        assertEquals(7.4, groups["NewGroup"]?.first()?.lon ?: 0.0, 1e-9)
    }

    @Test
    fun addFavoriteToExistingGroupWorks() = runTest {
        val (repo, _) = newRepository()
        assertTrue(repo.addGroup("Home"))

        assertTrue(repo.addFavorite("Home", "Work", 51.5, 7.4))

        val groups = repo.favorites.value
        assertEquals(listOf("Home"), groups.keys.toList())
        assertEquals("Work", groups["Home"]?.first()?.name)
    }

    @Test
    fun addFavoriteDoesNotDuplicateFavoriteInGroup() = runTest {
        val (repo, _) = newRepository()
        assertTrue(repo.addGroup("Home"))
        assertTrue(repo.addFavorite("Home", "Work", 51.5, 7.4))

        // Duplicate favorite name in an existing group is rejected.
        assertTrue(!repo.addFavorite("Home", "Work", 52.0, 8.0))

        assertEquals(1, repo.favorites.value["Home"]?.size)
    }

    // --- Reordering (spec fav-service — repository exposes a move method) ---

    @Test
    fun moveFavoriteReordersExposedState() = runTest {
        val (repo, _) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 52.5, 13.4))
        assertTrue(repo.addFavorite("Cities", "Paris", 48.9, 2.4))
        assertTrue(repo.addFavorite("Cities", "Rome", 41.9, 12.5))

        assertTrue(repo.moveFavorite("Cities", "Rome", 0))

        assertEquals(
            listOf("Rome", "Berlin", "Paris"),
            repo.favorites.value["Cities"]?.map { it.name }
        )
    }

    @Test
    fun moveFavoritePersistsExactlyOnce() = runTest {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 52.5, 13.4))
        assertTrue(repo.addFavorite("Cities", "Rome", 41.9, 12.5))
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.moveFavorite("Cities", "Rome", 0))

        assertEquals(savesBeforeMove + 1, client.saveFavoriteLocationsCalls.get())
        // The save path receives the favorites in their new order.
        assertEquals(
            listOf("Cities" to listOf("Rome", "Berlin")),
            client.lastSavedFavoriteOrder
        )
    }

    @Test
    fun failedMoveLeavesStateAndStoreUntouched() = runTest {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 52.5, 13.4))
        assertTrue(repo.addFavorite("Cities", "Rome", 41.9, 12.5))
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()
        client.moveFavoriteResult = false

        assertTrue(!repo.moveFavorite("Cities", "Rome", 0))

        assertEquals(
            listOf("Berlin", "Rome"),
            repo.favorites.value["Cities"]?.map { it.name }
        )
        assertEquals(savesBeforeMove, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun moveFavoriteForUnknownGroupOrFavoriteFails() = runTest {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 52.5, 13.4))
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()

        // The native store rejects both cases; neither may rewrite the file.
        assertTrue(!repo.moveFavorite("Missing", "Berlin", 0))
        assertTrue(!repo.moveFavorite("Cities", "Missing", 0))

        assertEquals(savesBeforeMove, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Berlin"),
            repo.favorites.value["Cities"]?.map { it.name }
        )
    }

    @Test
    fun moveFavoriteBeforeInitReturnsFalse() = runTest {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)

        assertTrue(!repo.moveFavorite("Cities", "Berlin", 0))

        assertEquals(0, client.moveFavoriteCalls.size)
    }

    // --- Starred order source (spec fav-starred-chip-bar) ---

    @Test
    fun starringAFavoriteIsReflectedInTheStarredList() = runTest {
        val (repo, _) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 1.0, 1.0))
        assertTrue(repo.addFavorite("Cities", "Paris", 2.0, 2.0))

        assertTrue(repo.setFavoriteStarred("Cities", "Berlin", true))
        assertEquals(listOf("Berlin"), repo.getAllStarredFavorites().map { it.second.name })

        assertTrue(repo.setFavoriteStarred("Cities", "Paris", true))
        assertEquals(
            listOf("Berlin", "Paris"),
            repo.getAllStarredFavorites().map { it.second.name }
        )
    }

    @Test
    fun starredFavoritesFollowGroupAndStoredOrder() = runTest {
        val (repo, _) = newRepository()
        // Added in the order the native store reports (groups are sorted by name
        // there; group ordering itself is a separate change).
        assertTrue(repo.addGroup("AGroup"))
        assertTrue(repo.addGroup("BGroup"))
        assertTrue(repo.addFavorite("BGroup", "Second", 1.0, 1.0))
        assertTrue(repo.addFavorite("BGroup", "First", 2.0, 2.0))
        assertTrue(repo.addFavorite("AGroup", "Only", 3.0, 3.0))
        assertTrue(repo.setFavoriteStarred("BGroup", "First", true))
        assertTrue(repo.setFavoriteStarred("BGroup", "Second", true))
        assertTrue(repo.setFavoriteStarred("AGroup", "Only", true))

        // Groups come first (map order), favorites in stored order inside a group.
        assertEquals(
            listOf("AGroup" to "Only", "BGroup" to "Second", "BGroup" to "First"),
            repo.getAllStarredFavorites().map { it.first to it.second.name }
        )

        // A reorder inside the group reorders its starred favorites too.
        assertTrue(repo.moveFavorite("BGroup", "First", 0))

        assertEquals(
            listOf("AGroup" to "Only", "BGroup" to "First", "BGroup" to "Second"),
            repo.getAllStarredFavorites().map { it.first to it.second.name }
        )
    }
}
