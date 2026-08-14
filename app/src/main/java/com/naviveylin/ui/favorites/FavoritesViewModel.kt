package com.naviveylin.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framstag.libosmscout.client.FavoriteLocation
import com.naviveylin.data.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val groups: Map<String, List<FavoriteLocation>> = emptyMap(),
    val selectedGroup: String? = null,
    val searchQuery: String = "",
    val snackbarMessage: String? = null,
    val starredFavorites: List<Pair<String, FavoriteLocation>> = emptyList(),
    val groupColors: Map<String, String> = emptyMap()
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

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
