package com.naviveylin.core.port

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.StarredFavoriteLocation
import kotlinx.coroutines.flow.StateFlow

/**
 * The favourites store a feature reads and writes — the OS and the native database abstracted.
 *
 * A `port` holds abstraction only (spec `package-structure` — port holds abstraction only): this file names
 * no Android type and no package of ours, so a feature reaches the favourites store through it and stays
 * Android-free (spec `package-structure` — a feature is Android-free).
 *
 * **The write serialisation is the implementation's, not this interface's.** The one writer of the
 * favourites store holds one write lock across `mutate + refreshState + persist` per operation
 * (`AGENTS.md`, Architecture), and the persist replaces the native store as a whole — so a write that
 * bypassed that lock could drop another write's favourite. An implementation therefore delegates each
 * call to the platform's favourites repository rather than reaching the native methods itself, and adds
 * no write path of its own: every `suspend` member here maps 1:1 onto a repository entry point that
 * already holds that lock, and the non-`suspend` members are reads.
 *
 * The state types are the bridge's ([FavoriteLocation], [StarredFavoriteLocation]) because the stored
 * order spans groups and reorders are reported through it; they are not Android types, so a feature may
 * hold them and is still testable without the Android runtime.
 */
interface FavouritesStore {

    /** The groups' contents, keyed by group name. */
    val favorites: StateFlow<Map<String, List<FavoriteLocation>>>

    /**
     * The stored group order, as its own channel.
     *
     * [favorites] carries a map, and map equality is content-based, so a reorder that changes nothing but
     * the sequence produces an equal map and a `StateFlow` would drop that emission. The order is a
     * `List`, which compares ordered, so it emits on every order change.
     */
    val groupOrder: StateFlow<List<String>>

    /** The starred favourites in their stored order, each entry naming the group that holds it. */
    val starredOrder: StateFlow<List<StarredFavoriteLocation>>

    /**
     * Whether the store's initialisation attempt has finished — success or failure.
     *
     * [favorites] is published from an empty map, so a caller cannot tell "not initialised yet" from
     * "initialised and holds no favourites" without this signal.
     */
    val storeReady: StateFlow<Boolean>

    // ---- Group CRUD ----

    /** Add a new empty group. Returns false if the name already exists. */
    suspend fun addGroup(name: String): Boolean

    /** Delete a group and all its favourites. Returns false if the group is not found. */
    suspend fun deleteGroup(name: String): Boolean

    /** Rename a group. Returns false if the old name is missing or the new one already exists. */
    suspend fun renameGroup(oldName: String, newName: String): Boolean

    // ---- Favourite CRUD ----

    /**
     * Add a favourite to a group, creating the group when it does not exist yet.
     * Returns false when the group cannot be created (duplicate name) or the favourite name is taken.
     */
    suspend fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double): Boolean

    /** Delete a favourite. Returns false if it is not found. */
    suspend fun deleteFavorite(groupName: String, favName: String): Boolean

    /** Rename a favourite. Returns false if it is not found or the new name already exists. */
    suspend fun renameFavorite(groupName: String, oldName: String, newName: String): Boolean

    /** Move a favourite to another position inside its group. Returns false if it is not found. */
    suspend fun moveFavorite(groupName: String, favName: String, newIndex: Int): Boolean

    /** Move a group to another position in the group order. Returns false if it is not found. */
    suspend fun moveGroup(groupName: String, newIndex: Int): Boolean

    /**
     * Move a favourite into another group, creating the target group when it does not exist yet.
     * Returns false when either group cannot be used or the target name is taken.
     */
    suspend fun moveFavoriteToGroup(
        sourceGroup: String,
        favName: String,
        targetGroup: String,
        newIndex: Int
    ): Boolean

    /** Move a starred favourite to another position in the starred order. Returns false if it is not found. */
    suspend fun moveStarredFavorite(groupName: String, favName: String, newIndex: Int): Boolean

    // ---- Group attributes ----

    /** Set a group's colour, or remove it with a null [colorHex]. Returns false if the group is not found. */
    suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean

    /** The colour assigned to a group as `#rrggbb`, or null when it carries none. */
    fun getGroupColor(groupName: String): String?

    // ---- Favourite attributes ----

    /** Star or unstar a favourite. Returns false if it is not found. */
    suspend fun setFavoriteStarred(groupName: String, favName: String, starred: Boolean): Boolean

    /** Whether a favourite carries the star today. */
    fun isFavoriteStarred(groupName: String, favName: String): Boolean

    // ---- Reads ----

    /** The group names in their stored order. */
    fun getGroupNames(): List<String>

    /** The group and entry holding a location, or null when no group holds one within tolerance. */
    fun findFavoriteByLocation(lat: Double, lon: Double): Pair<String, FavoriteLocation>?
}
