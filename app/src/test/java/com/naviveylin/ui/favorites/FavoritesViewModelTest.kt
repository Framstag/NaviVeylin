package com.naviveylin.ui.favorites

import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.data.FavoriteRepository
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

/**
 * Standalone fake that mirrors FavoriteRepository's interface for ViewModel testing.
 * Does NOT extend FavoriteRepository to avoid triggering OSMScoutClient's native lib loading.
 */
class FakeFavRepo {
    private val _favorites = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())
    val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = _favorites.asStateFlow()

    private val groups = mutableMapOf<String, MutableList<FavoriteLocation>>()
    private val groupColors = mutableMapOf<String, String>()
    private val starredFavs = mutableSetOf<Pair<String, String>>()

    val groupColorsSnapshot: Map<String, String> get() = groupColors.toMap()
    val starredSnapshot: Set<Pair<String, String>> get() = starredFavs.toSet()

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

    suspend fun deleteFavorite(groupName: String, favName: String): Boolean {
        val group = groups[groupName] ?: return false
        val removed = group.removeAll { it.name == favName }
        if (removed) {
            starredFavs.remove(groupName to favName)
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
        refreshState()
        return true
    }

    suspend fun renameGroup(oldName: String, newName: String): Boolean {
        if (!groups.containsKey(oldName) || groups.containsKey(newName)) return false
        val favs = groups.remove(oldName)!!
        groups[newName] = favs
        groupColors[newName] = groupColors.remove(oldName) ?: ""
        starredFavs.removeIf { it.first == oldName }
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
        } else {
            starredFavs.remove(groupName to favName)
        }
        refreshState()
        return true
    }

    fun isFavoriteStarred(groupName: String, favName: String): Boolean =
        starredFavs.contains(groupName to favName)

    fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> {
        val result = mutableListOf<Pair<String, FavoriteLocation>>()
        for ((groupName, favs) in _favorites.value) {
            for (fav in favs) {
                if (starredFavs.contains(groupName to fav.name)) {
                    result.add(groupName to fav)
                }
            }
        }
        return result
    }

    private fun refreshState() {
        _favorites.value = groups.mapValues { it.value.toList() }
    }
}

class FavoritesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
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
        assertEquals(1, repo.getAllStarredFavorites().size)
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

        assertEquals(2, repo.getAllStarredFavorites().size)
    }

    @Test
    fun `selectGroup updates selectedGroup`() = runTest(testDispatcher) {
        val repo = FakeFavRepo()
        val vm = FavoritesViewModel(
            object : FavoriteRepository(client = null as com.framstag.libosmscout.client.OSMScoutClient?) {
                override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repo.favorites
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
                override fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> = repo.getAllStarredFavorites()
            }
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
                override fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> = repo.getAllStarredFavorites()
            }
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
                override fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> = repo.getAllStarredFavorites()
            }
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
                override fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> = repo.getAllStarredFavorites()
            }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addFavorite("TestGroup", "NewFav", 10.0, 20.0)
        testDispatcher.scheduler.advanceUntilIdle()

        val favs = vm.uiState.value.groups["TestGroup"]
        assertTrue(favs?.any { it.name == "NewFav" } == true)
    }
}
