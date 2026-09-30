package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.naviveylin.R
import com.naviveylin.data.FavoriteRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Standalone fake that mirrors FavoriteRepository's interface for ViewModel testing.
 * Does NOT extend FavoriteRepository to avoid triggering OSMScoutClient's native lib loading.
 */
class FakeFavRepo {
    private val _favorites = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())
    val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = _favorites.asStateFlow()

    /**
     * The group order channel, mirroring `FavoriteRepository.groupOrder`: a `List`
     * compares ordered, so a reorder emits here even when the group map does not
     * change (which it does not when the groups are empty).
     */
    private val _groupOrder = MutableStateFlow<List<String>>(emptyList())
    val groupOrder: StateFlow<List<String>> = _groupOrder.asStateFlow()

    private val groups = mutableMapOf<String, MutableList<FavoriteLocation>>()
    private val groupColors = mutableMapOf<String, String>()
    private val starredFavs = mutableSetOf<Pair<String, String>>()

    /**
     * The starred order as (group, favorite name) pairs, in order. Mirrors the store's
     * one order across all groups: starring appends at the end, unstarring removes the
     * entry, a cross-group move keeps the entry's place (the favorite object travels).
     */
    private val starredOrderList = mutableListOf<Pair<String, String>>()

    /**
     * The starred-order channel, mirroring `FavoriteRepository.starredOrder`. A `List`
     * compares ordered, so a starred reorder emits here even when the group map does
     * not change.
     */
    private val _starredOrder = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())
    val starredOrder: StateFlow<List<StarredFavoriteLocation>> = _starredOrder.asStateFlow()

    val groupColorsSnapshot: Map<String, String> get() = groupColors.toMap()
    val starredSnapshot: Set<Pair<String, String>> get() = starredFavs.toSet()

    /** Number of [moveFavorite] invocations. */
    var moveFavoriteCalls = 0

    /** When set, [moveFavorite] waits for it — used to exercise the in-flight guard. */
    var moveFavoriteGate: CompletableDeferred<Unit>? = null

    /** When false, [moveFavorite] reports failure without changing the order. */
    var moveFavoriteSucceeds = true

    /** Number of [moveFavoriteToGroup] invocations. */
    var moveFavoriteToGroupCalls = 0

    /** When set, [moveFavoriteToGroup] waits for it — used to exercise the in-flight guard. */
    var moveFavoriteToGroupGate: CompletableDeferred<Unit>? = null

    /** When false, [moveFavoriteToGroup] reports failure without changing anything. */
    var moveFavoriteToGroupSucceeds = true

    /** Recorded [moveGroup] requests as (group name, target index), in call order. */
    val moveGroupCalls = mutableListOf<Pair<String, Int>>()

    /** When set, [moveGroup] waits for it — used to exercise the in-flight guard. */
    var moveGroupGate: CompletableDeferred<Unit>? = null

    /** When false, [moveGroup] reports failure without changing the order. */
    var moveGroupSucceeds = true

    suspend fun addGroup(name: String): Boolean {
        if (groups.containsKey(name)) return false
        groups[name] = mutableListOf()
        refreshState()
        return true
    }

    suspend fun deleteGroup(name: String): Boolean {
        if (!groups.containsKey(name)) return false
        groups.remove(name)
        groupColors.remove(name)
        starredFavs.removeIf { it.first == name }
        refreshState()
        return true
    }

    suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean {
        val group = groups[groupName] ?: return false
        if (group.any { it.name == favName }) return false
        group.add(FavoriteLocation(favName, lat, lon))
        refreshState()
        return true
    }

    suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean {
        moveFavoriteCalls++
        moveFavoriteGate?.await()
        if (!moveFavoriteSucceeds) return false
        val group = groups[groupName] ?: return false
        val currentIndex = group.indexOfFirst { it.name == favName }
        if (currentIndex < 0) return false
        val fav = group.removeAt(currentIndex)
        group.add(newIndex.coerceIn(0, group.size), fav)
        refreshState()
        return true
    }

    /**
     * Mirrors the repository contract: a missing destination group is created as
     * part of the move, an unknown source group or favorite fails, and a destination
     * already holding that name is refused without touching either group.
     */
    suspend fun moveFavoriteToGroup(
        sourceGroup: String, favName: String, targetGroup: String, newIndex: Int
    ): Boolean {
        moveFavoriteToGroupCalls++
        moveFavoriteToGroupGate?.await()
        if (!moveFavoriteToGroupSucceeds) return false
        if (!groups.containsKey(targetGroup)) groups[targetGroup] = mutableListOf()
        val source = groups[sourceGroup] ?: return false
        val target = groups[targetGroup] ?: return false
        val fav = source.find { it.name == favName } ?: return false
        if (target.any { it.name == favName }) return false
        source.remove(fav)
        target.add(newIndex.coerceIn(0, target.size), fav)
        val wasStarred = starredFavs.remove(sourceGroup to favName)
        if (wasStarred) {
            starredFavs.add(targetGroup to favName)
            // The order spans groups, so a cross-group move keeps the entry where it
            // is and only changes the group it is reported under.
            val index = starredOrderList.indexOfFirst {
                it.first == sourceGroup && it.second == favName
            }
            if (index >= 0) starredOrderList[index] = targetGroup to favName
        }
        refreshState()
        return true
    }

    /** Mirrors the repository contract: an unknown group fails, the index is clamped. */
    suspend fun moveGroup(groupName: String, newIndex: Int): Boolean {
        moveGroupCalls.add(groupName to newIndex)
        moveGroupGate?.await()
        if (!moveGroupSucceeds) return false
        if (!groups.containsKey(groupName)) return false
        val order = groups.keys.toMutableList()
        order.remove(groupName)
        order.add(newIndex.coerceIn(0, order.size), groupName)
        val reordered = order.associateWith { groups.getValue(it) }
        groups.clear()
        groups.putAll(reordered)
        refreshState()
        return true
    }

    suspend fun deleteFavorite(groupName: String, favName: String): Boolean {
        val group = groups[groupName] ?: return false
        val removed = group.removeAll { it.name == favName }
        if (removed) {
            starredFavs.remove(groupName to favName)
            starredOrderList.removeIf { it.first == groupName && it.second == favName }
            refreshState()
        }
        return removed
    }

    suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean {
        val group = groups[groupName] ?: return false
        val fav = group.find { it.name == oldName } ?: return false
        if (group.any { it.name == newName }) return false
        fav.name = newName
        starredFavs.removeIf { it.first == groupName && it.second == oldName }
        starredOrderList.replaceAll { entry ->
            if (entry.first == groupName && entry.second == oldName) groupName to newName else entry
        }
        refreshState()
        return true
    }

    suspend fun renameGroup(oldName: String, newName: String): Boolean {
        if (!groups.containsKey(oldName) || groups.containsKey(newName)) return false
        // The group keeps its position — only the name changes (native
        // `FavoriteLocationService::RenameGroup`); a remove+reinsert would move it to
        // the end and silently break the stored order (spec `group-ordering`).
        val renamed = groups.entries.associateTo(LinkedHashMap()) { (name, list) ->
            if (name == oldName) newName to list else name to list
        }
        groups.clear()
        groups.putAll(renamed)
        groupColors[newName] = groupColors.remove(oldName) ?: ""
        starredFavs.removeIf { it.first == oldName }
        starredOrderList.replaceAll { entry ->
            if (entry.first == oldName) newName to entry.second else entry
        }
        refreshState()
        return true
    }

    suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean {
        if (!groups.containsKey(groupName)) return false
        if (colorHex != null) {
            groupColors[groupName] = colorHex
        } else {
            groupColors.remove(groupName)
        }
        refreshState()
        return true
    }

    fun getGroupColor(groupName: String): String? = groupColors[groupName]

    suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean {
        val group = groups[groupName] ?: return false
        if (group.none { it.name == favName }) return false
        if (starred) {
            starredFavs.add(groupName to favName)
            if (starredOrderList.none { it.first == groupName && it.second == favName }) {
                starredOrderList.add(groupName to favName)
            }
        } else {
            starredFavs.remove(groupName to favName)
            starredOrderList.removeIf { it.first == groupName && it.second == favName }
        }
        refreshState()
        return true
    }

    fun isFavoriteStarred(groupName: String, favName: String): Boolean =
        starredFavs.contains(groupName to favName)

    /** Number of [moveStarredFavorite] invocations. */
    var moveStarredFavoriteCalls = 0

    /** When set, [moveStarredFavorite] waits for it — used to exercise the in-flight guard. */
    var moveStarredFavoriteGate: CompletableDeferred<Unit>? = null

    /** When false, [moveStarredFavorite] reports failure without changing the order. */
    var moveStarredFavoriteSucceeds = true

    /**
     * Mirrors the repository contract for a starred reorder: the target index is
     * clamped over the order after the entry was removed, and an unknown entry or a
     * favorite that is not starred fails with the order untouched.
     */
    suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean {
        moveStarredFavoriteCalls++
        moveStarredFavoriteGate?.await()
        if (!moveStarredFavoriteSucceeds) return false
        val currentIndex = starredOrderList.indexOfFirst {
            it.first == groupName && it.second == favName
        }
        if (currentIndex < 0) return false
        val moved = starredOrderList.removeAt(currentIndex)
        starredOrderList.add(newIndex.coerceIn(0, starredOrderList.size), moved)
        refreshState()
        return true
    }

    private fun refreshState() {
        // Fresh FavoriteLocation objects per refresh, like the JNI bridge (which
        // reconstructs them from the native store). A StateFlow drops an emission
        // whose value equals the current one, and Map equality ignores order — so
        // without the copies a group reorder (same keys, same favorites, new
        // sequence) would never reach the ViewModel's collector.
        _favorites.value = groups.mapValues { (_, favs) ->
            favs.map { fav ->
                FavoriteLocation(fav.name, fav.lat, fav.lon).also { copy ->
                    copy.attributes.putAll(fav.attributes)
                }
            }
        }
        _groupOrder.value = groups.keys.toList()
        // The starred order is published from its own list: the entry sequence is not a
        // property of the group map, and a star change must not reorder anything else.
        _starredOrder.value = starredOrderList.mapNotNull { (groupName, favName) ->
            val fav = _favorites.value[groupName]?.firstOrNull { it.name == favName }
                ?: return@mapNotNull null
            StarredFavoriteLocation(groupName, fav)
        }
    }
}

/**
 * Runs under Robolectric for the resource-backed messages the ViewModel builds
 * (`context.getString`). The class deliberately never instantiates
 * [com.framstag.libosmscout.client.FakeOSMScoutClient], so it does not need the
 * native stub and must not set `@Config`/`@GraphicsMode`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setGroupColor updates groupColors`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("TestGroup")

        repo.setGroupColor("TestGroup", "#FF0000")

        assertEquals("#FF0000", repo.groupColorsSnapshot["TestGroup"])
    }

    @Test
    fun `toggleStar adds to starredFavorites`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("TestGroup")
        repo.addFavorite("TestGroup", "Fav1", 1.0, 2.0)

        repo.setFavoriteStarred("TestGroup", "Fav1", true)

        assertTrue(repo.starredSnapshot.contains("TestGroup" to "Fav1"))
        assertEquals(1, repo.starredOrder.value.size)
    }

    @Test
    fun `toggleStar twice removes from starredFavorites`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("TestGroup")
        repo.addFavorite("TestGroup", "Fav1", 1.0, 2.0)

        repo.setFavoriteStarred("TestGroup", "Fav1", true)
        repo.setFavoriteStarred("TestGroup", "Fav1", false)

        assertTrue(repo.starredSnapshot.isEmpty())
    }

    @Test
    fun `multiple starred favorites all appear`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Group1")
        repo.addGroup("Group2")
        repo.addFavorite("Group1", "Fav1", 1.0, 2.0)
        repo.addFavorite("Group1", "Fav2", 3.0, 4.0)
        repo.addFavorite("Group2", "Fav3", 5.0, 6.0)

        repo.setFavoriteStarred("Group1", "Fav1", true)
        repo.setFavoriteStarred("Group1", "Fav2", true)

        assertEquals(2, repo.starredOrder.value.size)
    }

    @Test
    fun `selectGroup updates selectedGroup`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        val vm = FavoritesViewModel(
            object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
                override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
                override val groupOrder: StateFlow<List<String>> = repo.groupOrder
                override suspend fun addGroup(name: String): Boolean = repo.addGroup(name)
                override suspend fun deleteGroup(name: String): Boolean = repo.deleteGroup(name)
                override suspend fun renameGroup(oldName: String, newName: String): Boolean = repo.renameGroup(oldName, newName)
                override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean = repo.addFavorite(groupName, favName, lat, lon)
                override suspend fun deleteFavorite(groupName: String, favName: String): Boolean = repo.deleteFavorite(groupName, favName)
                override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean = repo.renameFavorite(groupName, oldName, newName)
                override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean = repo.setGroupColor(groupName, colorHex)
                override fun getGroupColor(groupName: String): String? = repo.getGroupColor(groupName)
                override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean = repo.setFavoriteStarred(groupName, favName, starred)
                override fun isFavoriteStarred(groupName: String, favName: String): Boolean = repo.isFavoriteStarred(groupName, favName)
                override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repo.starredOrder
                override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                    repo.moveStarredFavorite(groupName, favName, newIndex)
            },
            context
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectGroup("TestGroup")
        assertEquals("TestGroup", vm.uiState.value.selectedGroup)

        vm.selectGroup(null)
        assertEquals(null, vm.uiState.value.selectedGroup)
    }

    @Test
    fun `addGroup creates group and shows snackbar`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        val vm = FavoritesViewModel(
            object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
                override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
                override val groupOrder: StateFlow<List<String>> = repo.groupOrder
                override suspend fun addGroup(name: String): Boolean = repo.addGroup(name)
                override suspend fun deleteGroup(name: String): Boolean = repo.deleteGroup(name)
                override suspend fun renameGroup(oldName: String, newName: String): Boolean = repo.renameGroup(oldName, newName)
                override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean = repo.addFavorite(groupName, favName, lat, lon)
                override suspend fun deleteFavorite(groupName: String, favName: String): Boolean = repo.deleteFavorite(groupName, favName)
                override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean = repo.renameFavorite(groupName, oldName, newName)
                override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean = repo.setGroupColor(groupName, colorHex)
                override fun getGroupColor(groupName: String): String? = repo.getGroupColor(groupName)
                override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean = repo.setFavoriteStarred(groupName, favName, starred)
                override fun isFavoriteStarred(groupName: String, favName: String): Boolean = repo.isFavoriteStarred(groupName, favName)
                override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repo.starredOrder
                override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                    repo.moveStarredFavorite(groupName, favName, newIndex)
            },
            context
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addGroup("NewGroup")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.groups.containsKey("NewGroup"))
        assertTrue(vm.uiState.value.snackbarMessage != null)
    }

    @Test
    fun `deleteGroup removes group`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("TestGroup")
        val vm = FavoritesViewModel(
            object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
                override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
                override val groupOrder: StateFlow<List<String>> = repo.groupOrder
                override suspend fun addGroup(name: String): Boolean = repo.addGroup(name)
                override suspend fun deleteGroup(name: String): Boolean = repo.deleteGroup(name)
                override suspend fun renameGroup(oldName: String, newName: String): Boolean = repo.renameGroup(oldName, newName)
                override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean = repo.addFavorite(groupName, favName, lat, lon)
                override suspend fun deleteFavorite(groupName: String, favName: String): Boolean = repo.deleteFavorite(groupName, favName)
                override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean = repo.renameFavorite(groupName, oldName, newName)
                override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean = repo.setGroupColor(groupName, colorHex)
                override fun getGroupColor(groupName: String): String? = repo.getGroupColor(groupName)
                override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean = repo.setFavoriteStarred(groupName, favName, starred)
                override fun isFavoriteStarred(groupName: String, favName: String): Boolean = repo.isFavoriteStarred(groupName, favName)
                override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repo.starredOrder
                override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                    repo.moveStarredFavorite(groupName, favName, newIndex)
            },
            context
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteGroup("TestGroup")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.uiState.value.groups.containsKey("TestGroup"))
    }

    @Test
    fun `addFavorite adds to group`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("TestGroup")
        val vm = FavoritesViewModel(
            object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
                override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
                override val groupOrder: StateFlow<List<String>> = repo.groupOrder
                override suspend fun addGroup(name: String): Boolean = repo.addGroup(name)
                override suspend fun deleteGroup(name: String): Boolean = repo.deleteGroup(name)
                override suspend fun renameGroup(oldName: String, newName: String): Boolean = repo.renameGroup(oldName, newName)
                override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean = repo.addFavorite(groupName, favName, lat, lon)
                override suspend fun deleteFavorite(groupName: String, favName: String): Boolean = repo.deleteFavorite(groupName, favName)
                override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean = repo.renameFavorite(groupName, oldName, newName)
                override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean = repo.setGroupColor(groupName, colorHex)
                override fun getGroupColor(groupName: String): String? = repo.getGroupColor(groupName)
                override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean = repo.setFavoriteStarred(groupName, favName, starred)
                override fun isFavoriteStarred(groupName: String, favName: String): Boolean = repo.isFavoriteStarred(groupName, favName)
                override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repo.starredOrder
                override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                    repo.moveStarredFavorite(groupName, favName, newIndex)
            },
            context
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addFavorite("TestGroup", "NewFav", 10.0, 20.0)
        testDispatcher.scheduler.advanceUntilIdle()

        val favs = vm.uiState.value.groups["TestGroup"]
        assertTrue(favs?.any { it.name == "NewFav" } == true)
    }

    // --- Reordering (spec fav-ordering — position; spec fav-management-ui — serialised commits) ---

    /**
     * Builds a ViewModel over [repo]; the anonymous repository mirrors the same
     * override set the existing tests use, plus the reorder operation.
     */
    private fun viewModel(repo: FakeFavRepo) = FavoritesViewModel(
        object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
            override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
            override val groupOrder: StateFlow<List<String>> = repo.groupOrder
            override suspend fun addGroup(name: String): Boolean = repo.addGroup(name)
            override suspend fun deleteGroup(name: String): Boolean = repo.deleteGroup(name)
            override suspend fun renameGroup(oldName: String, newName: String): Boolean = repo.renameGroup(oldName, newName)
            override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean = repo.addFavorite(groupName, favName, lat, lon)
            override suspend fun deleteFavorite(groupName: String, favName: String): Boolean = repo.deleteFavorite(groupName, favName)
            override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean = repo.renameFavorite(groupName, oldName, newName)
            override suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                repo.moveFavorite(groupName, favName, newIndex)
            override suspend fun moveFavoriteToGroup(
                sourceGroup: String, favName: String, targetGroup: String, newIndex: Int
            ): Boolean = repo.moveFavoriteToGroup(sourceGroup, favName, targetGroup, newIndex)
            override suspend fun moveGroup(groupName: String, newIndex: Int): Boolean =
                repo.moveGroup(groupName, newIndex)
            override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean = repo.setGroupColor(groupName, colorHex)
            override fun getGroupColor(groupName: String): String? = repo.getGroupColor(groupName)
            override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean = repo.setFavoriteStarred(groupName, favName, starred)
            override fun isFavoriteStarred(groupName: String, favName: String): Boolean = repo.isFavoriteStarred(groupName, favName)
            override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repo.starredOrder
            override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
                repo.moveStarredFavorite(groupName, favName, newIndex)
        },
        context
    )

    @Test
    fun `moveFavorite reorders the group and shows no error`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(null, vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `failed moveFavorite keeps the order and reports it`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        repo.moveFavoriteSucceeds = false
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Berlin", "Rome"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertTrue(vm.uiState.value.snackbarMessage != null)
    }

    @Test
    fun `reorder commit while one is in flight is dropped`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val gate = CompletableDeferred<Unit>()
        repo.moveFavoriteGate = gate
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        // The first move is parked inside the repository; the second must not start.
        vm.moveFavorite("Cities", "Berlin", 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.moveFavoriteCalls)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.moveFavoriteCalls)
        assertEquals(listOf("Rome", "Berlin"), vm.uiState.value.groups["Cities"]?.map { it.name })
    }

    @Test
    fun `a later reorder is accepted after the in-flight one finished`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.moveFavorite("Cities", "Rome", 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, repo.moveFavoriteCalls)
        assertEquals(listOf("Berlin", "Rome"), vm.uiState.value.groups["Cities"]?.map { it.name })
    }

    // --- Cross-group move (spec fav-service — cross-group move; spec fav-management-ui) ---

    @Test
    fun `moveFavoriteToGroup moves the favorite and reports success`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        repo.addFavorite("Work", "Office", 5.0, 6.0)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavoriteToGroup("Cities", "Rome", "Work", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Berlin"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(listOf("Rome", "Office"), vm.uiState.value.groups["Work"]?.map { it.name })
        assertEquals(
            context.getString(R.string.favorite_moved_to_group, "Rome", "Work"),
            vm.uiState.value.snackbarMessage
        )
    }

    @Test
    fun `moveFavoriteToGroup into a new group creates it`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavoriteToGroup("Cities", "Rome", "Fresh", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.groups["Fresh"]?.map { it.name } == listOf("Rome"))
        assertEquals(
            context.getString(R.string.favorite_moved_to_group, "Rome", "Fresh"),
            vm.uiState.value.snackbarMessage
        )
    }

    @Test
    fun `refused cross-group move reports the name conflict and keeps both groups`() =
        runTest(testDispatcher) {
            val repo = FakeFavRepo()
            repo.addGroup("Cities")
            repo.addGroup("Work")
            repo.addFavorite("Cities", "Office", 1.0, 2.0)
            repo.addFavorite("Work", "Office", 5.0, 6.0)
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveFavoriteToGroup("Cities", "Office", "Work", 0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Office"), vm.uiState.value.groups["Cities"]?.map { it.name })
            assertEquals(listOf("Office"), vm.uiState.value.groups["Work"]?.map { it.name })
            assertEquals(
                context.getString(R.string.favorite_move_name_conflict, "Work", "Office"),
                vm.uiState.value.snackbarMessage
            )
        }

    @Test
    fun `failed cross-group move reports the move failure`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        repo.moveFavoriteToGroupSucceeds = false
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavoriteToGroup("Cities", "Rome", "Work", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Rome"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(
            context.getString(R.string.favorite_move_failed, "Rome"),
            vm.uiState.value.snackbarMessage
        )
    }

    @Test
    fun `cross-group move while a reorder is in flight is dropped`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val gate = CompletableDeferred<Unit>()
        repo.moveFavoriteGate = gate
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.moveFavoriteToGroup("Cities", "Berlin", "Work", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.moveFavoriteToGroupCalls)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Rome", "Berlin"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(0, repo.moveFavoriteToGroupCalls)
    }

    @Test
    fun `reorder while a cross-group move is in flight is dropped`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val gate = CompletableDeferred<Unit>()
        repo.moveFavoriteToGroupGate = gate
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavoriteToGroup("Cities", "Berlin", "Work", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.moveFavoriteCalls)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Rome"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(listOf("Berlin"), vm.uiState.value.groups["Work"]?.map { it.name })
        assertEquals(0, repo.moveFavoriteCalls)
    }

    @Test
    fun `a later cross-group move is accepted after the in-flight one finished`() =
        runTest(testDispatcher) {
            val repo = FakeFavRepo()
            repo.addGroup("Cities")
            repo.addGroup("Work")
            repo.addGroup("Other")
            repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
            repo.addFavorite("Cities", "Rome", 3.0, 4.0)
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveFavoriteToGroup("Cities", "Berlin", "Work", 0)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.moveFavoriteToGroup("Cities", "Rome", "Other", 0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, repo.moveFavoriteToGroupCalls)
            assertEquals(listOf("Berlin"), vm.uiState.value.groups["Work"]?.map { it.name })
            assertEquals(listOf("Rome"), vm.uiState.value.groups["Other"]?.map { it.name })
        }

    // --- Group reorder (spec group-ordering — position; spec fav-service — move method for groups) ---

    @Test
    fun `moveGroup reorders the groups and shows no error`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addGroup("Home")
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveGroup("Home", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Home", "Cities", "Work"), vm.uiState.value.groupOrder)
        assertEquals(
            setOf("Home", "Cities", "Work"),
            vm.uiState.value.groups.keys
        )
        assertEquals(listOf("Home" to 0), repo.moveGroupCalls)
        assertEquals(null, vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `failed moveGroup keeps the order and reports it`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Home")
        repo.moveGroupSucceeds = false
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveGroup("Home", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Cities", "Home"), vm.uiState.value.groupOrder)
        assertEquals(
            context.getString(R.string.group_move_failed, "Home"),
            vm.uiState.value.snackbarMessage
        )
    }

    @Test
    fun `group reorder while a reorder is in flight is dropped`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Home")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val gate = CompletableDeferred<Unit>()
        repo.moveFavoriteGate = gate
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.moveGroup("Home", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.moveGroupCalls.size)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Cities", "Home"), vm.uiState.value.groupOrder)
        assertEquals(0, repo.moveGroupCalls.size)
    }

    @Test
    fun `reorder while a group reorder is in flight is dropped`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Home")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        val gate = CompletableDeferred<Unit>()
        repo.moveGroupGate = gate
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveGroup("Home", 0)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.moveFavorite("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.moveFavoriteCalls)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Home", "Cities"), vm.uiState.value.groupOrder)
        assertEquals(listOf("Berlin", "Rome"), vm.uiState.value.groups["Cities"]?.map { it.name })
        assertEquals(0, repo.moveFavoriteCalls)
    }

    @Test
    fun `a later group reorder is accepted after the in-flight one finished`() =
        runTest(testDispatcher) {
            val repo = FakeFavRepo()
            repo.addGroup("Cities")
            repo.addGroup("Work")
            repo.addGroup("Home")
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveGroup("Home", 0)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.moveGroup("Work", 0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf("Work", "Home", "Cities"), vm.uiState.value.groupOrder)
            assertEquals(listOf("Home" to 0, "Work" to 0), repo.moveGroupCalls)
        }

    // --- Starred reorder (spec starred-ordering — position; spec fav-starred-chip-bar) ---

    private suspend fun repoWithStars(): FakeFavRepo {
        val repo = FakeFavRepo()
        repo.addGroup("Cities")
        repo.addGroup("Work")
        repo.addFavorite("Cities", "Berlin", 1.0, 2.0)
        repo.addFavorite("Cities", "Rome", 3.0, 4.0)
        repo.addFavorite("Work", "Office", 5.0, 6.0)
        return repo
    }

    @Test
    fun `moveStarred reorders the chips and shows no error`() = runTest(testDispatcher) {
        val repo = repoWithStars()
        repo.setFavoriteStarred("Cities", "Berlin", true)
        repo.setFavoriteStarred("Cities", "Rome", true)
        repo.setFavoriteStarred("Work", "Office", true)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        // One order across groups: the last chip moves ahead of both others.
        vm.moveStarred("Work", "Office", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("Work" to "Office", "Cities" to "Berlin", "Cities" to "Rome"),
            vm.uiState.value.starredFavorites.map { it.first to it.second.name }
        )
        assertEquals(1, repo.moveStarredFavoriteCalls)
        assertEquals(null, vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `failed moveStarred keeps the order and reports it`() = runTest(testDispatcher) {
        val repo = repoWithStars()
        repo.setFavoriteStarred("Cities", "Berlin", true)
        repo.setFavoriteStarred("Cities", "Rome", true)
        repo.moveStarredFavoriteSucceeds = false
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.moveStarred("Cities", "Rome", 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("Berlin", "Rome"),
            vm.uiState.value.starredFavorites.map { it.second.name }
        )
        assertEquals(
            context.getString(R.string.star_move_failed, "Rome"),
            vm.uiState.value.snackbarMessage
        )
    }

    @Test
    fun `starred reorder while another order write is in flight is dropped`() =
        runTest(testDispatcher) {
            val repo = repoWithStars()
            repo.setFavoriteStarred("Cities", "Berlin", true)
            repo.setFavoriteStarred("Cities", "Rome", true)
            val gate = CompletableDeferred<Unit>()
            repo.moveFavoriteGate = gate
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveFavorite("Cities", "Rome", 0)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.moveStarred("Cities", "Rome", 0)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(0, repo.moveStarredFavoriteCalls)

            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(0, repo.moveStarredFavoriteCalls)
        }

    @Test
    fun `another order write while a starred reorder is in flight is dropped`() =
        runTest(testDispatcher) {
            val repo = repoWithStars()
            repo.setFavoriteStarred("Cities", "Berlin", true)
            repo.setFavoriteStarred("Cities", "Rome", true)
            val gate = CompletableDeferred<Unit>()
            repo.moveStarredFavoriteGate = gate
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveStarred("Cities", "Rome", 0)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.moveFavorite("Cities", "Berlin", 1)
            vm.moveGroup("Work", 0)
            vm.moveFavoriteToGroup("Work", "Office", "Cities", 0)
            testDispatcher.scheduler.advanceUntilIdle()

            // One shared guard for all four order writes.
            assertEquals(0, repo.moveFavoriteCalls)
            assertEquals(0, repo.moveGroupCalls.size)
            assertEquals(0, repo.moveFavoriteToGroupCalls)

            gate.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                listOf("Rome", "Berlin"),
                vm.uiState.value.starredFavorites.map { it.second.name }
            )
        }

    @Test
    fun `a later starred reorder is accepted after the in-flight one finished`() =
        runTest(testDispatcher) {
            val repo = repoWithStars()
            repo.setFavoriteStarred("Cities", "Berlin", true)
            repo.setFavoriteStarred("Cities", "Rome", true)
            val vm = viewModel(repo)
            testDispatcher.scheduler.advanceUntilIdle()

            vm.moveStarred("Cities", "Rome", 0)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.moveStarred("Cities", "Rome", 1)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, repo.moveStarredFavoriteCalls)
            assertEquals(
                listOf("Berlin", "Rome"),
                vm.uiState.value.starredFavorites.map { it.second.name }
            )
        }

    @Test
    fun `the starred channel reaches the UI state across groups`() = runTest(testDispatcher) {
        val repo = repoWithStars()
        repo.setFavoriteStarred("Work", "Office", true)
        repo.setFavoriteStarred("Cities", "Berlin", true)
        val vm = viewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("Work" to "Office", "Cities" to "Berlin"),
            vm.uiState.value.starredFavorites.map { it.first to it.second.name }
        )

        // Starring appends at the end of the order.
        repo.setFavoriteStarred("Cities", "Rome", true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            listOf("Office", "Berlin", "Rome"),
            vm.uiState.value.starredFavorites.map { it.second.name }
        )

        // Unstarring removes the entry and keeps the rest in order.
        repo.setFavoriteStarred("Cities", "Berlin", false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            listOf("Office", "Rome"),
            vm.uiState.value.starredFavorites.map { it.second.name }
        )
    }
}
