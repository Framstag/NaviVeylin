package com.naviveylin.data

import com.framstag.libosmscout.client.FakeOSMScoutClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // --- Cross-group move (spec fav-service — cross-group move method; spec fav-ordering) ---

    /** Two groups with two favorites each, in a fixed stored order. */
    private suspend fun repoWithTwoGroups(): Pair<FavoriteRepository, FakeOSMScoutClient> {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addGroup("Work"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 52.5, 13.4))
        assertTrue(repo.addFavorite("Cities", "Rome", 41.9, 12.5))
        assertTrue(repo.addFavorite("Work", "Office", 51.5, 7.4))
        return repo to client
    }

    @Test
    fun moveFavoriteToGroupMovesTheFavoriteBetweenGroups() = runTest {
        val (repo, _) = repoWithTwoGroups()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Work", 0))

        val groups = repo.favorites.value
        assertEquals(listOf("Berlin"), groups["Cities"]?.map { it.name })
        assertEquals(listOf("Rome", "Office"), groups["Work"]?.map { it.name })
    }

    @Test
    fun moveFavoriteToGroupPersistsExactlyOnce() = runTest {
        val (repo, client) = repoWithTwoGroups()
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Work", 1))

        assertEquals(savesBefore + 1, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Cities" to listOf("Berlin"), "Work" to listOf("Office", "Rome")),
            client.lastSavedFavoriteOrder
        )
    }

    @Test
    fun moveFavoriteToGroupKeepsCoordinatesStarAndAttributes() = runTest {
        val (repo, client) = repoWithTwoGroups()
        assertTrue(repo.setFavoriteStarred("Cities", "Rome", true))
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Work", 0))

        val moved = repo.favorites.value["Work"]?.firstOrNull { it.name == "Rome" }
        assertEquals(41.9, moved?.lat ?: 0.0, 1e-9)
        assertEquals(12.5, moved?.lon ?: 0.0, 1e-9)
        assertTrue(repo.isFavoriteStarred("Work", "Rome"))
        assertEquals("true", moved?.attributes?.get("starred"))
        assertEquals(savesBefore + 1, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun refusedNameCollisionLeavesBothGroupsUntouched() = runTest {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addGroup("Work"))
        assertTrue(repo.addFavorite("Cities", "Office", 52.5, 13.4))
        assertTrue(repo.addFavorite("Cities", "Rome", 41.9, 12.5))
        assertTrue(repo.addFavorite("Work", "Office", 51.5, 7.4))
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        // The native store refuses the collision before removing anything.
        assertTrue(!repo.moveFavoriteToGroup("Cities", "Office", "Work", 0))

        assertEquals(listOf("Office", "Rome"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Office"), repo.favorites.value["Work"]?.map { it.name })
        assertEquals(savesBefore, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun moveFavoriteToGroupForUnknownGroupsOrFavoriteFails() = runTest {
        val (repo, client) = repoWithTwoGroups()
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(!repo.moveFavoriteToGroup("Missing", "Rome", "Work", 0))
        assertTrue(!repo.moveFavoriteToGroup("Cities", "Missing", "Work", 0))

        // A failing move does not touch the file.
        assertEquals(savesBefore, client.saveFavoriteLocationsCalls.get())
        assertEquals(listOf("Berlin", "Rome"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Office"), repo.favorites.value["Work"]?.map { it.name })
    }

    @Test
    fun moveFavoriteToGroupBeforeInitReturnsFalse() = runTest {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)

        assertTrue(!repo.moveFavoriteToGroup("Cities", "Berlin", "Work", 0))

        assertEquals(0, client.moveFavoriteToGroupCalls.size)
    }

    @Test
    fun moveFavoriteToGroupRunsOffTheMainThread() = runTest {
        val (repo, client) = repoWithTwoGroups()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Work", 0))

        val call = client.moveFavoriteToGroupCalls.single()
        assertEquals("Cities", call.sourceGroup)
        assertEquals("Rome", call.favName)
        assertEquals("Work", call.targetGroup)
        assertEquals(0, call.newIndex)
        assertTrue(
            "the JNI move must not run on the main thread (was '${call.threadName}')",
            call.threadName != android.os.Looper.getMainLooper().thread.name
        )
    }

    @Test
    fun moveFavoriteToGroupCreatesTheMissingDestinationGroup() = runTest {
        val (repo, client) = repoWithTwoGroups()
        val savesBefore = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Fresh", 0))

        assertEquals(listOf("Berlin"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Rome"), repo.favorites.value["Fresh"]?.map { it.name })
        // Group creation persists on its own, then the move persists again — the
        // same two-step sequence as adding a favorite to a missing group. Sorted by
        // group name so the assertion does not depend on the fake's insertion order
        // (the native store reports groups sorted by name).
        assertEquals(savesBefore + 2, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Cities" to listOf("Berlin"), "Fresh" to listOf("Rome"), "Work" to listOf("Office")),
            client.lastSavedFavoriteOrder.sortedBy { it.first }
        )
    }

    @Test
    fun moveFavoriteToGroupToItsOwnGroupSucceedsWithoutChangingAnything() = runTest {
        val (repo, _) = repoWithTwoGroups()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Cities", 0))

        assertEquals(listOf("Berlin", "Rome"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Office"), repo.favorites.value["Work"]?.map { it.name })
    }

    @Test
    fun moveFavoriteToGroupClampsTargetIndexBeyondTheEnd() = runTest {
        val (repo, _) = repoWithTwoGroups()

        assertTrue(repo.moveFavoriteToGroup("Cities", "Berlin", "Work", 100))

        assertEquals(listOf("Rome"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Office", "Berlin"), repo.favorites.value["Work"]?.map { it.name })
    }

    /**
     * A cross-group move racing a reorder inside a group: both effects survive,
     * which is the serialisation property the repository owns (the ViewModel's own
     * guard is about the visible sequence, not about this).
     */
    @Test
    fun crossGroupMoveOverlappingAReorderKeepsBothEffects() = runTest {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)
        repo.init("/tmp/favorites-cross-group-overlap-test.json")
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addGroup("Work"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 1.0, 1.0))
        assertTrue(repo.addFavorite("Cities", "Rome", 2.0, 2.0))
        assertTrue(repo.addFavorite("Cities", "Oslo", 3.0, 3.0))
        assertTrue(repo.addFavorite("Work", "Office", 4.0, 4.0))

        val move = async { repo.moveFavoriteToGroup("Cities", "Oslo", "Work", 0) }
        val reorder = async { repo.moveFavorite("Cities", "Berlin", 1) }

        assertTrue(move.await())
        assertTrue(reorder.await())

        assertEquals(listOf("Rome", "Berlin"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Oslo", "Office"), repo.favorites.value["Work"]?.map { it.name })
        assertEquals(
            listOf(
                "Cities" to listOf("Rome", "Berlin"),
                "Work" to listOf("Oslo", "Office")
            ),
            client.lastSavedFavoriteOrder
        )
    }

    // --- Group order (spec fav-service — repository move method for groups; spec group-ordering) ---

    /** Three groups in a fixed stored order, one favorite each. */
    private suspend fun repoWithThreeGroups(): Pair<FavoriteRepository, FakeOSMScoutClient> {
        val (repo, client) = newRepository()
        assertTrue(repo.addGroup("Cities"))
        assertTrue(repo.addGroup("Work"))
        assertTrue(repo.addGroup("Home"))
        assertTrue(repo.addFavorite("Cities", "Berlin", 1.0, 1.0))
        assertTrue(repo.addFavorite("Work", "Office", 2.0, 2.0))
        assertTrue(repo.addFavorite("Home", "Flat", 3.0, 3.0))
        return repo to client
    }

    @Test
    fun moveGroupReordersExposedStateAndKeepsEveryGroupsFavorites() = runTest {
        val (repo, client) = repoWithThreeGroups()

        assertTrue(repo.moveGroup("Home", 0))

        assertEquals(listOf("Home", "Cities", "Work"), repo.favorites.value.keys.toList())
        // Only the group order changes: every group keeps its own favorites.
        assertEquals(listOf("Flat"), repo.favorites.value["Home"]?.map { it.name })
        assertEquals(listOf("Berlin"), repo.favorites.value["Cities"]?.map { it.name })
        assertEquals(listOf("Office"), repo.favorites.value["Work"]?.map { it.name })
        val call = client.moveGroupCalls.single()
        assertEquals("Home", call.groupName)
        assertEquals(0, call.newIndex)
    }

    @Test
    fun moveGroupPersistsExactlyOnce() = runTest {
        val (repo, client) = repoWithThreeGroups()
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()

        assertTrue(repo.moveGroup("Home", 0))

        assertEquals(savesBeforeMove + 1, client.saveFavoriteLocationsCalls.get())
        assertEquals(
            listOf("Home", "Cities", "Work"),
            client.lastSavedFavoriteOrder.map { it.first }
        )
    }

    @Test
    fun moveGroupClampsTheTargetIndexAndAcceptsANegativeOne() = runTest {
        val (repo, _) = repoWithThreeGroups()

        // Beyond the end: the group lands last (spec group-ordering).
        assertTrue(repo.moveGroup("Cities", 100))
        assertEquals(listOf("Work", "Home", "Cities"), repo.favorites.value.keys.toList())

        // Negative: the first position.
        assertTrue(repo.moveGroup("Home", -1))
        assertEquals(listOf("Home", "Work", "Cities"), repo.favorites.value.keys.toList())
    }

    @Test
    fun failedMoveGroupLeavesStateAndStoreUntouched() = runTest {
        val (repo, client) = repoWithThreeGroups()
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()
        client.moveGroupResult = false

        assertTrue(!repo.moveGroup("Home", 0))

        assertEquals(listOf("Cities", "Work", "Home"), repo.favorites.value.keys.toList())
        assertEquals(savesBeforeMove, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun moveGroupForAnUnknownGroupFails() = runTest {
        val (repo, client) = repoWithThreeGroups()
        val savesBeforeMove = client.saveFavoriteLocationsCalls.get()

        assertTrue(!repo.moveGroup("Missing", 0))

        assertEquals(listOf("Cities", "Work", "Home"), repo.favorites.value.keys.toList())
        assertEquals(savesBeforeMove, client.saveFavoriteLocationsCalls.get())
    }

    @Test
    fun moveGroupBeforeInitReturnsFalse() = runTest {
        val client = FakeOSMScoutClient()
        val repo = FavoriteRepository(client)

        assertTrue(!repo.moveGroup("Cities", 0))

        assertEquals(0, client.moveGroupCalls.size)
    }

    @Test
    fun moveGroupRunsOffTheMainThread() = runTest {
        val (repo, client) = repoWithThreeGroups()

        assertTrue(repo.moveGroup("Home", 0))

        val call = client.moveGroupCalls.single()
        assertTrue(
            "the JNI move must not run on the main thread (was '${call.threadName}')",
            call.threadName != android.os.Looper.getMainLooper().thread.name
        )
    }

    /**
     * The exposed key order is the native group order (spec group-ordering — the
     * order every group-listing surface renders). The repository keeps the order by
     * building its map in `getFavoriteGroups()` order, so this pins that the map is
     * still order-preserving after a move.
     */
    @Test
    fun emittedGroupOrderMatchesTheNativeGroupOrder() = runTest {
        val (repo, client) = repoWithThreeGroups()

        repo.moveGroup("Home", 0)
        repo.moveGroup("Work", 0)

        assertEquals(
            client.getFavoriteGroups().map { it.name }.toList(),
            repo.favorites.value.keys.toList()
        )
    }

    /**
     * A group move racing a favorite write: the write lock keeps both effects, and
     * the persisted file carries the moved group order and the new favorite.
     */
    @Test
    fun groupMoveOverlappingAWriteKeepsBothEffects() = runTest {
        val (repo, client) = repoWithThreeGroups()

        val move = async { repo.moveGroup("Home", 0) }
        val add = async { repo.addFavorite("Cities", "Rome", 4.0, 4.0) }

        assertTrue(move.await())
        assertTrue(add.await())

        assertEquals(listOf("Home", "Cities", "Work"), repo.favorites.value.keys.toList())
        assertEquals(
            listOf("Berlin", "Rome"),
            repo.favorites.value["Cities"]?.map { it.name }
        )
        assertEquals(
            listOf("Home", "Cities", "Work"),
            client.lastSavedFavoriteOrder.map { it.first }
        )
        assertEquals(
            listOf("Berlin", "Rome"),
            client.lastSavedFavoriteOrder.first { it.first == "Cities" }.second
        )
    }

    @Test
    fun renamingAGroupKeepsItsPositionInTheOrder() = runTest {
        val (repo, _) = repoWithThreeGroups()

        assertTrue(repo.renameGroup("Work", "Büro"))

        // The native store renames in place (`FavoriteLocationService::RenameGroup`);
        // a rename must not move the group to the end of the order.
        assertEquals(listOf("Cities", "Büro", "Home"), repo.favorites.value.keys.toList())
        assertEquals(listOf("Cities", "Büro", "Home"), repo.groupOrder.value)
    }

    @Test
    fun deletingAGroupKeepsTheRelativeOrderOfTheRest() = runTest {
        val (repo, _) = repoWithThreeGroups()

        assertTrue(repo.deleteGroup("Work"))

        assertEquals(listOf("Cities", "Home"), repo.favorites.value.keys.toList())
        assertEquals(listOf("Cities", "Home"), repo.groupOrder.value)
    }

    @Test
    fun aFavoriteChangeDoesNotMoveItsGroup() = runTest {
        val (repo, _) = repoWithThreeGroups()
        val orderBefore = repo.groupOrder.value

        assertTrue(repo.addFavorite("Work", "Desk", 9.0, 9.0))
        assertTrue(repo.setFavoriteStarred("Work", "Desk", true))
        assertTrue(repo.renameFavorite("Work", "Desk", "Desk2"))
        assertTrue(repo.setGroupColor("Work", "FF5733"))

        assertEquals(orderBefore, repo.groupOrder.value)
    }

    /**
     * An order-only change has to reach collectors.
     *
     * A StateFlow drops an emission equal to its current value, and `Map` equality
     * ignores iteration order, so a group reorder is observable only as long as the
     * map's contents are not value-equal. The bridge hands the repository fresh
     * `FavoriteLocation` objects on every read (that class has no `equals`), which is
     * what makes this hold today: if that ever stops being true — a value-equal
     * `FavoriteLocation`, or a cached group array — the UI would silently keep
     * rendering the old group order and this assertion fails first.
     */
    @Test
    fun anOrderOnlyGroupChangeEmitsAStateThatDiffersFromThePreviousOne() = runTest {
        val (repo, _) = repoWithThreeGroups()
        val before = repo.favorites.value

        assertTrue(repo.moveGroup("Home", 0))

        val after = repo.favorites.value
        assertNotEquals("an order-only change must not be an equal map", before, after)
        assertEquals(listOf("Home", "Cities", "Work"), after.keys.toList())
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
    fun starredFavoritesFollowAGroupChange() = runTest {
        val (repo, _) = repoWithTwoGroups()
        assertTrue(repo.setFavoriteStarred("Cities", "Rome", true))
        assertEquals(
            listOf("Cities" to "Rome"),
            repo.getAllStarredFavorites().map { it.first to it.second.name }
        )

        assertTrue(repo.moveFavoriteToGroup("Cities", "Rome", "Work", 0))

        // The star belongs to the favorite; the group it is reported under follows
        // the move (spec fav-ordering — a starred favorite follows its new group).
        assertEquals(
            listOf("Work" to "Rome"),
            repo.getAllStarredFavorites().map { it.first to it.second.name }
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
