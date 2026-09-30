package com.naviveylin.data

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.StarredFavoriteLocation
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository wrapping JNI CRUD for favorite location groups and favorites.
 *
 * Exposes reactive state via [StateFlow] and delegates all persistence
 * to the existing C++ [FavoriteLocationService] through [OSMScoutClient].
 */
@Singleton
open class FavoriteRepository @Inject constructor(
    private val client: OSMScoutClient? = null
) {
    private val _favorites = MutableStateFlow<Map<String, List<FavoriteLocation>>>(emptyMap())
    open val favorites: StateFlow<Map<String, List<FavoriteLocation>>> = _favorites.asStateFlow()

    private val _groupOrder = MutableStateFlow<List<String>>(emptyList())

    /**
     * The group order, as its own channel.
     *
     * [favorites] carries the groups' contents as a map, and `Map` equality is
     * content-based, so a reorder that changes nothing but the sequence produces a
     * map equal to the previous one — a `StateFlow` would drop that emission and
     * every consumer iterating the map would keep the old order. A `List` compares
     * ordered, so this flow emits on every order change and is the order the phone
     * grid and the car place list render. It is written together with [favorites] in
     * [refreshState], so the two never describe different stores.
     */
    open val groupOrder: StateFlow<List<String>> = _groupOrder.asStateFlow()

    private val _starredOrder = MutableStateFlow<List<StarredFavoriteLocation>>(emptyList())

    /**
     * The starred favorites in their stored order, each entry naming the group that
     * holds it (spec `starred-ordering`).
     *
     * The store owns this order: it spans all groups, so the group map cannot express
     * it, and its rules (the fallback for a file without stored positions, the tie
     * breaks, the clamping) live in the native service. Deriving the sequence here by
     * iterating the map would both lose the order and duplicate those rules. It is
     * written in [refreshState] together with [favorites] and [groupOrder], so the
     * three always describe one store.
     */
    open val starredOrder: StateFlow<List<StarredFavoriteLocation>> = _starredOrder.asStateFlow()

    private var favoritesFile: String? = null
    private var loaded = false

    /**
     * Serialises the write operations against each other.
     *
     * Each write is a read-modify-write triple: mutate the native store, then
     * [refreshState] and [persist]. The persist replaces the whole native store
     * with the data it is handed, so two writes that interleave could persist a
     * snapshot that predates the other write's mutation and drop it. Holding
     * this lock across the whole triple makes one write atomic with respect to
     * the others, so no successful write is lost.
     *
     * Not reentrant: the private `*Locked` helpers assume the lock is already
     * held, and a nested write (see [addFavoriteLocked]) calls those rather than
     * a public entry point.
     */
    private val writeMutex = Mutex()

    /**
     * Dispatcher for all JNI/file persistence work. Test hook: point it at a
     * TestDispatcher shared with the test's `runTest` so CRUD is deterministic.
     */
    @VisibleForTesting
    internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Initialise the repository with a file path for persistence.
     * Must be called once (typically from MapCanvasViewModel.initMap).
     */
    suspend fun init(filePath: String): Boolean = withContext(defaultDispatcher) {
        favoritesFile = filePath
        val dir = File(filePath).parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()

        val success = client!!.loadFavoriteLocations(filePath)
        loaded = true
        refreshState()
        success
    }

    /** Reload state from JNI. */
    private fun refreshState() {
        val groups = client!!.favoriteGroups ?: emptyArray()
        val map = mutableMapOf<String, List<FavoriteLocation>>()
        for (group in groups) {
            map[group.name] = group.favorites.toList()
        }
        _favorites.value = map
        _groupOrder.value = map.keys.toList()
        _starredOrder.value = client!!.getStarredFavorites()?.toList() ?: emptyList()
    }

    /** Persist current state to JSON file. */
    private suspend fun persist() = withContext(defaultDispatcher) {
        val path = favoritesFile ?: return@withContext
        val groups = client!!.favoriteGroups ?: return@withContext
        client!!.saveFavoriteLocations(path, groups)
    }

    // ---- Group CRUD ----

    /** Add a new empty group. Returns false if name already exists. */
    open suspend fun addGroup(name: String): Boolean = writeMutex.withLock {
        addGroupLocked(name)
    }

    /**
     * Add a group with the write lock already held, so a caller that is inside
     * the critical section (see [addFavoriteLocked]) does not re-enter the
     * non-reentrant [writeMutex].
     */
    private suspend fun addGroupLocked(name: String): Boolean = withContext(defaultDispatcher) {
        if (!loaded) return@withContext false
        val success = client!!.addGroup(name)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    /** Delete a group and all its favorites. Returns false if not found. */
    open suspend fun deleteGroup(name: String): Boolean = writeMutex.withLock {
        deleteGroupLocked(name)
    }

    private suspend fun deleteGroupLocked(name: String): Boolean = withContext(defaultDispatcher) {
        if (!loaded) return@withContext false
        val success = client!!.deleteGroup(name)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    /** Rename a group. Returns false if old name not found or new name already exists. */
    open suspend fun renameGroup(oldName: String, newName: String): Boolean = writeMutex.withLock {
        renameGroupLocked(oldName, newName)
    }

    private suspend fun renameGroupLocked(oldName: String, newName: String): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val success = client!!.renameGroup(oldName, newName)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    // ---- Favorite CRUD ----

    /** Add a favorite to a group. Creates the group first if it does not exist yet. Returns false if group creation fails (duplicate name) or duplicate favorite name. */
    open suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean =
        writeMutex.withLock {
            addFavoriteLocked(groupName, favName, lat, lon)
        }

    private suspend fun addFavoriteLocked(groupName: String, favName: String, lat: Double, lon: Double): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            // Auto-create the group (e.g. the details-sheet "+ New group" flow).
            // If creation fails (name already exists), adding fails as well. The
            // locking helper is used because the write lock is already held here.
            if (groupName !in _favorites.value.keys) {
                val created = addGroupLocked(groupName)
                if (!created) return@withContext false
            }
            val success = client!!.addFavorite(groupName, favName, lat, lon)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    /** Delete a favorite from a group. Returns false if not found. */
    open suspend fun deleteFavorite(groupName: String, favName: String): Boolean =
        writeMutex.withLock {
            deleteFavoriteLocked(groupName, favName)
        }

    private suspend fun deleteFavoriteLocked(groupName: String, favName: String): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val success = client!!.deleteFavorite(groupName, favName)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    /** Rename a favorite within a group. Returns false if old not found or new name exists. */
    open suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean =
        writeMutex.withLock {
            renameFavoriteLocked(groupName, oldName, newName)
        }

    private suspend fun renameFavoriteLocked(groupName: String, oldName: String, newName: String): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val success = client!!.renameFavorite(groupName, oldName, newName)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    /**
     * Move a favorite to a new position within its group. The target index is
     * 0-based over the group's favorite list after the favorite has been removed
     * from its current position; out-of-range indices are clamped by the native
     * store. Returns false if not loaded, or if the group/favorite is unknown.
     *
     * The move is persisted with a single write, so the whole reorder is one
     * JNI call plus one file write.
     */
    open suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
        writeMutex.withLock {
            moveFavoriteLocked(groupName, favName, newIndex)
        }

    private suspend fun moveFavoriteLocked(groupName: String, favName: String, newIndex: Int): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val success = client!!.moveFavorite(groupName, favName, newIndex)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    /**
     * Move a group to a new position in the group order.
     *
     * The target index is 0-based over the group order after the group has been
     * removed from its current position; out-of-range indices are clamped by the
     * native store, and a negative index means the first position. The order is
     * what [favorites] emits, so the whole reorder is one JNI call plus one file
     * write.
     *
     * Returns false if not loaded, or if the group is unknown.
     */
    open suspend fun moveGroup(groupName: String, newIndex: Int): Boolean =
        writeMutex.withLock {
            moveGroupLocked(groupName, newIndex)
        }

    private suspend fun moveGroupLocked(groupName: String, newIndex: Int): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val success = client!!.moveGroup(groupName, newIndex)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    /**
     * Move a favorite out of [sourceGroup] and into [targetGroup].
     *
     * The target index is 0-based over the destination group's list as it stands
     * before the move (unlike [moveFavorite], whose index refers to its own group's
     * list after the favorite was removed); out-of-range indices are clamped by the
     * native store, and a negative index means the first position. The favorite
     * keeps its coordinates, its attributes and its star.
     *
     * A group that does not exist yet is created first, so a caller can move into a
     * brand-new group in one operation (the same two-step persist as adding a
     * favorite to a missing group: group creation, then the move). A destination
     * that already holds a favorite of that name is refused by the native store with
     * both groups untouched, and nothing is persisted.
     *
     * Returns false if not loaded, if either group or the favorite is unknown, or if
     * the destination already holds that name.
     */
    open suspend fun moveFavoriteToGroup(
        sourceGroup: String,
        favName: String,
        targetGroup: String,
        newIndex: Int
    ): Boolean = writeMutex.withLock {
        moveFavoriteToGroupLocked(sourceGroup, favName, targetGroup, newIndex)
    }

    private suspend fun moveFavoriteToGroupLocked(
        sourceGroup: String,
        favName: String,
        targetGroup: String,
        newIndex: Int
    ): Boolean = withContext(defaultDispatcher) {
        if (!loaded) return@withContext false
        // Auto-create the destination group, like addFavoriteLocked does: a caller
        // may move a favorite into a group that does not exist yet. The locking
        // helper is used because the write lock is already held here.
        if (targetGroup !in _favorites.value.keys) {
            val created = addGroupLocked(targetGroup)
            if (!created) return@withContext false
        }
        val success = client!!.moveFavoriteToGroup(sourceGroup, favName, targetGroup, newIndex)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    /**
     * Move a starred favorite to a new position in the starred order.
     *
     * The target index is 0-based over the starred order after the favorite has been
     * removed from its current position; out-of-range indices are clamped by the native
     * store, and a negative index means the first position. The order spans all groups,
     * so this is the one move that can carry an entry past a favorite of another group.
     *
     * Returns false if not loaded, or if the group or the favorite is unknown, or if
     * the favorite is not starred.
     */
    open suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean =
        writeMutex.withLock {
            moveStarredFavoriteLocked(groupName, favName, newIndex)
        }

    private suspend fun moveStarredFavoriteLocked(
        groupName: String,
        favName: String,
        newIndex: Int
    ): Boolean = withContext(defaultDispatcher) {
        if (!loaded) return@withContext false
        val success = client!!.moveStarredFavorite(groupName, favName, newIndex)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    // ---- Group Attributes ----

    /** Set a color for a group. Pass null to remove the color. */
    open suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean =
        writeMutex.withLock {
            setGroupColorLocked(groupName, colorHex)
        }

    private suspend fun setGroupColorLocked(groupName: String, colorHex: String?): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            // Strip # prefix if present; C++ expects 6 hex chars
            val cleanColor = colorHex?.trimStart('#') ?: ""
            val ok = client!!.setGroupColor(groupName, cleanColor)
            if (ok) {
                refreshState()
                persist()
            }
            ok
        }

    /** Get the assigned color for a group, or null if none. */
    open fun getGroupColor(groupName: String): String? {
        val color = client!!.getGroupColor(groupName)
        if (color.isNullOrEmpty()) return null
        return "#$color" // Add # prefix for Android Color.parseColor()
    }

    // ---- Favorite Attributes ----

    /** Star or unstar a favorite. */
    open suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean =
        writeMutex.withLock {
            setFavoriteStarredLocked(groupName, favName, starred)
        }

    private suspend fun setFavoriteStarredLocked(groupName: String, favName: String, starred: Boolean): Boolean =
        withContext(defaultDispatcher) {
            if (!loaded) return@withContext false
            val ok = client!!.setStarred(groupName, favName, starred)
            if (ok) {
                refreshState()
                persist()
            }
            ok
        }

    /** Check if a favorite is starred. */
    open fun isFavoriteStarred(groupName: String, favName: String): Boolean {
        return client!!.isStarred(groupName, favName)
    }

    /** Get all group names. */
    fun getGroupNames(): List<String> = _favorites.value.keys.toList()

    /** Check if a location (by lat/lon) is already favorited in any group. */
    fun findFavoriteByLocation(lat: Double, lon: Double): Pair<String, FavoriteLocation>? {
        val tolerance = 0.0001 // ~11m at equator
        for ((groupName, favs) in _favorites.value) {
            for (fav in favs) {
                if (kotlin.math.abs(fav.lat - lat) < tolerance &&
                    kotlin.math.abs(fav.lon - lon) < tolerance
                ) {
                    return groupName to fav
                }
            }
        }
        return null
    }
}
