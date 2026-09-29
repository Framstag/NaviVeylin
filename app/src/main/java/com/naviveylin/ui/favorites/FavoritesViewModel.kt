package com.naviveylin.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.R
import com.naviveylin.data.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val groups: Map<String, List<FavoriteLocation>> = emptyMap(),
    /**
     * The stored group order (spec `group-ordering`). Read from the repository's own
     * order flow, not from [groups]' iteration order: a reorder leaves the map
     * contents equal, so the map flow may not re-emit at all.
     */
    val groupOrder: List<String> = emptyList(),
    val selectedGroup: String? = null,
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    val starredFavorites: List<Pair<String, FavoriteLocation>> = emptyList(),
    val groupColors: Map<String, String> = emptyMap()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    /**
     * True while an order-changing write (a reorder commit, a cross-group move or a
     * group reorder) is being persisted. Read and written from the main thread
     * (`viewModelScope`); volatile so the coroutine continuation of the completing
     * move observes the current value.
     *
     * All three kinds go through this one flag: two of them racing each other would
     * otherwise show an order the user did not ask for (the repository's write lock
     * keeps each write whole, so this is about the visible sequence, not about data
     * integrity).
     */
    @Volatile
    private var orderWriteInFlight = false

    init {
        viewModelScope.launch {
            favoriteRepository.favorites.collect { groups ->
                val starred = favoriteRepository.getAllStarredFavorites()
                val colors = groups.keys.associateWith { groupName ->
                    favoriteRepository.getGroupColor(groupName)
                }.filterValues { it != null }.mapValues { it.value!! }
                _uiState.value = _uiState.value.copy(
                    groups = groups,
                    starredFavorites = starred,
                    groupColors = colors
                )
            }
        }
        // Second collector, because the two channels are independent: a group
        // reorder changes only the order, and the map flow above may not emit for it.
        viewModelScope.launch {
            favoriteRepository.groupOrder.collect { order ->
                _uiState.value = _uiState.value.copy(groupOrder = order)
            }
        }
    }

    fun selectGroup(name: String?) {
        _uiState.value = _uiState.value.copy(selectedGroup = name)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun addGroup(name: String) {
        viewModelScope.launch {
            val success = favoriteRepository.addGroup(name)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Group '$name' created" else "Group '$name' already exists"
            )
        }
    }

    fun deleteGroup(name: String) {
        viewModelScope.launch {
            val success = favoriteRepository.deleteGroup(name)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Group '$name' deleted" else "Failed to delete group"
            )
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            val success = favoriteRepository.renameGroup(oldName, newName)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Group renamed to '$newName'" else "Group name '$newName' already exists"
            )
        }
    }

    fun addFavorite(groupName: String, favName: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val success = favoriteRepository.addFavorite(groupName, favName, lat, lon)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Added '$favName'" else "Failed to add favorite"
            )
        }
    }

    fun deleteFavorite(groupName: String, favName: String) {
        viewModelScope.launch {
            val success = favoriteRepository.deleteFavorite(groupName, favName)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Deleted '$favName'" else "Failed to delete"
            )
        }
    }

    fun renameFavorite(groupName: String, oldName: String, newName: String) {
        viewModelScope.launch {
            val success = favoriteRepository.renameFavorite(groupName, oldName, newName)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = if (success) "Renamed to '$newName'" else "Failed to rename"
            )
        }
    }

    /**
     * Move a favorite to a new position within its group.
     *
     * A reorder commit arriving while another order write is still being persisted
     * is dropped: the two writes would otherwise interleave and the later state
     * refresh could restore an order the user did not ask for.
     */
    fun moveFavorite(groupName: String, favName: String, newIndex: Int) {
        if (orderWriteInFlight) return
        orderWriteInFlight = true
        viewModelScope.launch {
            try {
                val success = favoriteRepository.moveFavorite(groupName, favName, newIndex)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = "Failed to reorder '$favName'"
                    )
                }
            } finally {
                orderWriteInFlight = false
            }
        }
    }

    /**
     * Move a group to a new position in the group order.
     *
     * Shares the in-flight guard with [moveFavorite] and [moveFavoriteToGroup]:
     * all three change the visible order, and a group drag landing between another
     * order write's JNI call and its state refresh would show an order the user did
     * not ask for. The failure message names the group, because a refusal here means
     * the store no longer holds it (deleted while it was being dragged).
     */
    fun moveGroup(groupName: String, newIndex: Int) {
        if (orderWriteInFlight) return
        orderWriteInFlight = true
        viewModelScope.launch {
            try {
                val success = favoriteRepository.moveGroup(groupName, newIndex)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = context.getString(R.string.group_move_failed, groupName)
                    )
                }
            } finally {
                orderWriteInFlight = false
            }
        }
    }

    /**
     * Move a favorite out of its group and into another one, creating the
     * destination group when it does not exist yet.
     *
     * Shares the in-flight guard with [moveFavorite] and [moveGroup]: all three are
     * order writes. A
     * refusal because the destination already holds that name is reported with a
     * message naming the group and the favorite, since that is the one failure the
     * user can act on (rename or delete the name that is in the way).
     */
    fun moveFavoriteToGroup(
        sourceGroup: String,
        favName: String,
        targetGroup: String,
        newIndex: Int
    ) {
        if (orderWriteInFlight) return
        orderWriteInFlight = true
        // Read the conflict before the move: only the message depends on it, and a
        // move that is refused changes no state to read it from afterwards.
        val destinationHasThatName = favoriteRepository.favorites.value[targetGroup]
            ?.any { it.name == favName } == true
        viewModelScope.launch {
            try {
                val success = favoriteRepository.moveFavoriteToGroup(
                    sourceGroup, favName, targetGroup, newIndex
                )
                val message = when {
                    success -> context.getString(
                        R.string.favorite_moved_to_group, favName, targetGroup
                    )

                    destinationHasThatName -> context.getString(
                        R.string.favorite_move_name_conflict, targetGroup, favName
                    )

                    else -> context.getString(R.string.favorite_move_failed, favName)
                }
                _uiState.value = _uiState.value.copy(snackbarMessage = message)
            } finally {
                orderWriteInFlight = false
            }
        }
    }

    fun setGroupColor(groupName: String, colorHex: String?) {
        viewModelScope.launch {
            favoriteRepository.setGroupColor(groupName, colorHex)
        }
    }

    fun toggleStar(groupName: String, favName: String) {
        viewModelScope.launch {
            val currentlyStarred = favoriteRepository.isFavoriteStarred(groupName, favName)
            favoriteRepository.setFavoriteStarred(groupName, favName, !currentlyStarred)
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}
