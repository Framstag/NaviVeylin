package com.naviveylin.data

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    // --- Write serialisation (spec fav-service — overlapping write operations are serialised) ---

    /**
     * Deterministic lost-update probe.
     *
     * Write A is parked inside its persist step (after it took its snapshot,
     * before it recorded the save) while write B runs to completion. A is then
     * released, so without write serialisation A's older snapshot is the last
     * one written and B's group is gone from the persisted data — and from the
     * native store, which the real save path rebuilds from that array.
     */
    @Test
    fun overlappingWritesKeepBothWrites() = runBlocking {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)
        repo.init("/tmp/favorites-overlap-test.json")

        val firstWriteInSave = java.util.concurrent.CountDownLatch(1)
        val releaseFirstWrite = java.util.concurrent.CountDownLatch(1)
        val saves = java.util.concurrent.atomic.AtomicInteger(0)

        client.beforeSaveFavoriteLocations = {
            if (saves.incrementAndGet() == 1) {
                firstWriteInSave.countDown()
                // With serialisation in place the second write cannot start, so
                // this expires and the first write continues.
                releaseFirstWrite.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
        }

        // Deliberately not runTest: the hook blocks a real worker thread, and a
        // latch wait on the virtual-time test thread would never let the
        // launched writers start.
        val first = launch(Dispatchers.Default) { repo.addGroup("First") }
        assertTrue(
            "the first write should reach its save step",
            firstWriteInSave.await(2, java.util.concurrent.TimeUnit.SECONDS)
        )

        val second = launch(Dispatchers.Default) { repo.addGroup("Second") }
        second.join()
        releaseFirstWrite.countDown()
        first.join()

        // Both writes survive in the exposed state ...
        assertEquals(listOf("First", "Second"), repo.favorites.value.keys.toList())
        // ... and in the data the last save handed to the store.
        assertEquals(
            listOf("First", "Second"),
            client.lastSavedFavoriteOrder.map { it.first }
        )
    }

    /**
     * Many overlapping writes: every one of them survives, which is the
     * property the write serialisation has to hold under real UI tap rates.
     */
    @Test
    fun allConcurrentWritesSurvive() = runTest {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)
        repo.init("/tmp/favorites-concurrent-test.json")

        val writes = (0 until 8).map { index ->
            async { repo.addFavorite("Group$index", "Fav$index", 51.0 + index, 7.0 + index) }
        }
        writes.forEach { assertTrue(it.await()) }

        assertEquals(
            (0 until 8).map { "Group$it" },
            repo.favorites.value.keys.toList()
        )
        assertEquals(
            (0 until 8).map { "Group$it" },
            client.lastSavedFavoriteOrder.map { it.first }
        )
    }

    @Test
    fun sequentialWritesEachPersistExactlyOnce() = runTest {
        val (repo, client) = newRepository()
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.addGroup("Home"))
        assertTrue(repo.addFavorite("Home", "Work", 51.5, 7.4))

        assertEquals(savesBefore + 2, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Home" to listOf("Work")),
            client.lastSavedFavoriteOrder
        )
    }

    @Test
    fun failedWritePersistsNothing() = runTest {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Home"))
        assertTrue(repo.addFavorite("Home", "Work", 51.5, 7.4))
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(!repo.renameGroup("Missing", "Renamed"))
        assertTrue(!repo.addFavorite("Home", "Work", 1.0, 2.0))
        assertTrue(!repo.deleteFavorite("Home", "Missing"))

        assertEquals(savesBefore, client.saveFavoriteLocationsCalls.get())
        assertEquals(listOf("Home"), repo.favorites.value.keys.toList())
        assertEquals(listOf("Work"), repo.favorites.value["Home"]?.map { it.name })
    }

    /**
     * Adding to a group that does not exist yet creates the group and the
     * favorite inside the same critical section. Creating the group persists on
     * its own and the favorite persists again, which is the pre-existing
     * sequence; what this guards is that the nested locked call cannot
     * self-deadlock and that both end up stored and persisted.
     */
    @Test
    fun addingToAMissingGroupCompletesAndPersistsBoth() = runTest {
        val (repo, client) = newRepository()

        assertTrue(repo.addFavorite("Fresh", "Place", 51.5, 7.4))

        assertEquals(listOf("Fresh"), repo.favorites.value.keys.toList())
        assertEquals(listOf("Place"), repo.favorites.value["Fresh"]?.map { it.name })
        assertEquals(
            listOf("Fresh" to listOf("Place")),
            client.lastSavedFavoriteOrder
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
