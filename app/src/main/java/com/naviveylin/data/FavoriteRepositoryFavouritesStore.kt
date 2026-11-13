package com.naviveylin.data

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import com.naviveylin.core.port.FavouritesStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The favourites store's platform side: [FavouritesStore] implemented by delegating to
 * [FavoriteRepository].
 *
 * A feature reaches the store through the port (spec `package-structure` — port holds abstraction only)
 * and this class is the only thing behind it, so the port has exactly one implementation to select in
 * `di` (spec `package-structure` — shell is the only kind that hosts several features: selecting a port
 * implementation is shell's).
 *
 * **It holds no lock and writes nothing itself.** Every member is a call into [FavoriteRepository], which
 * holds one write lock across `mutate + refreshState + persist` per operation (`AGENTS.md`, Architecture):
 * a second lock here would order the two locks differently from the store's own mutex and could persist a
 * snapshot that predates a concurrent write, and a direct native call would bypass the repository's lock
 * entirely. Delegation is therefore the whole of this class — it is the seam, not a second writer.
 */
@Singleton
class FavoriteRepositoryFavouritesStore @Inject constructor(
    private val repository: FavoriteRepository
) : FavouritesStore {

    override val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = repository.favorites

    override val groupOrder: StateFlow<List<String>> = repository.groupOrder

    override val starredOrder: StateFlow<List<StarredFavoriteLocation>> = repository.starredOrder

    override val storeReady: StateFlow<Boolean> = repository.storeReady

    // ---- Group CRUD ----

    override suspend fun addGroup(name: String): Boolean = repository.addGroup(name)

    override suspend fun deleteGroup(name: String): Boolean = repository.deleteGroup(name)

    override suspend fun renameGroup(oldName: String, newName: String): Boolean =
        repository.renameGroup(oldName, newName)

    // ---- Favourite CRUD ----

    override suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean =
        repository.addFavorite(groupName, favName, lat, lon)

    override suspend fun deleteFavorite(groupName: String, favName: String): Boolean =
        repository.deleteFavorite(groupName, favName)

    override suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean =
        repository.renameFavorite(groupName, oldName, newName)

    override suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
        repository.moveFavorite(groupName, favName, newIndex)

    override suspend fun moveGroup(groupName: String, newIndex: Int): Boolean =
        repository.moveGroup(groupName, newIndex)

    override suspend fun moveFavoriteToGroup(
        sourceGroup: String,
        favName: String,
        targetGroup: String,
        newIndex: Int
    ): Boolean = repository.moveFavoriteToGroup(sourceGroup, favName, targetGroup, newIndex)

    override suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
        repository.moveStarredFavorite(groupName, favName, newIndex)

    // ---- Group attributes ----

    override suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean =
        repository.setGroupColor(groupName, colorHex)

    override fun getGroupColor(groupName: String): String? = repository.getGroupColor(groupName)

    // ---- Favourite attributes ----

    override suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean =
        repository.setFavoriteStarred(groupName, favName, starred)

    override fun isFavoriteStarred(groupName: String, favName: String): Boolean =
        repository.isFavoriteStarred(groupName, favName)

    // ---- Reads ----

    override fun getGroupNames(): List<String> = repository.getGroupNames()

    override fun findFavoriteByLocation(lat: Double, lon: Double): Pair<String, FavoriteLocation>? =
        repository.findFavoriteByLocation(lat, lon)
}
