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
}
