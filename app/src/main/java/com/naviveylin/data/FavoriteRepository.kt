package com.naviveylin.data

import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.OSMScoutClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var favoritesFile: String? = null
    private var loaded = false

    /**
     * Initialise the repository with a file path for persistence.
     * Must be called once (typically from MapCanvasViewModel.initMap).
     */
    suspend fun init(filePath: String): Boolean = withContext(Dispatchers.Default) {
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
    }

    /** Persist current state to JSON file. */
    private suspend fun persist() = withContext(Dispatchers.Default) {
        val path = favoritesFile ?: return@withContext
        val groups = client!!.favoriteGroups ?: return@withContext
        client!!.saveFavoriteLocations(path, groups)
    }

    // ---- Group CRUD ----

    /** Add a new empty group. Returns false if name already exists. */
    open suspend fun addGroup(name: String): Boolean = withContext(Dispatchers.Default) {
        if (!loaded) return@withContext false
        val success = client!!.addGroup(name)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    /** Delete a group and all its favorites. Returns false if not found. */
    open suspend fun deleteGroup(name: String): Boolean = withContext(Dispatchers.Default) {
        if (!loaded) return@withContext false
        val success = client!!.deleteGroup(name)
        if (success) {
            refreshState()
            persist()
        }
        success
    }

    /** Rename a group. Returns false if old name not found or new name already exists. */
    open suspend fun renameGroup(oldName: String, newName: String): Boolean = withContext(Dispatchers.Default) {
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
        withContext(Dispatchers.Default) {
            if (!loaded) return@withContext false
            // Auto-create the group (e.g. the details-sheet "+ New group" flow).
            // If creation fails (name already exists), adding fails as well.
            if (groupName !in _favorites.value.keys) {
                val created = addGroup(groupName)
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
        withContext(Dispatchers.Default) {
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
        withContext(Dispatchers.Default) {
            if (!loaded) return@withContext false
            val success = client!!.renameFavorite(groupName, oldName, newName)
            if (success) {
                refreshState()
                persist()
            }
            success
        }

    // ---- Group Attributes ----

    /** Set a color for a group. Pass null to remove the color. */
    open suspend fun setGroupColor(groupName: String, colorHex: String?): Boolean =
        withContext(Dispatchers.Default) {
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
        withContext(Dispatchers.Default) {
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

    /** Get all starred favorites across all groups. */
    open fun getAllStarredFavorites(): List<Pair<String, FavoriteLocation>> {
        val result = mutableListOf<Pair<String, FavoriteLocation>>()
        for ((groupName, favs) in _favorites.value) {
            for (fav in favs) {
                if (fav.attributes["starred"] == "true") {
                    result.add(groupName to fav)
                }
            }
        }
        return result
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
