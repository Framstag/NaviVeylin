package com.naviveylin.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.FavoriteLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSheet(
    mapCenterLat: Double,
    mapCenterLon: Double,
    onDismiss: () -> Unit,
    onFavoriteClick: (FavoriteLocation) -> Unit = {},
    onChipRouteTo: (FavoriteLocation) -> Unit = {},
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reset to main screen every time the sheet opens
    LaunchedEffect(Unit) {
        viewModel.selectGroup(null)
    }

    // Show snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Dialog states
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf<String?>(null) }
    var showRenameGroupDialog by remember { mutableStateOf<String?>(null) }
    var showAddFavDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteFavDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showRenameFavDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAddMapLocDialog by remember { mutableStateOf(false) }
    var showColorPickerGroup by remember { mutableStateOf<String?>(null) }
    var targetScrollFavName by remember { mutableStateOf<String?>(null) }
    val groupDetailListState = remember { androidx.compose.foundation.lazy.LazyListState() }

    val isDetailView = state.selectedGroup != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isDetailView) {
                        Text(state.selectedGroup ?: "")
                    } else {
                        Text("Favorites")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDetailView) {
                            viewModel.selectGroup(null)
                        } else {
                            onDismiss()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isDetailView) {
                        IconButton(onClick = { showAddGroupDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add group")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.groups.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No favorites yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Search for a location and add it to favorites",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { showAddMapLocDialog = true }) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Add current map location")
                }
            }
        } else if (isDetailView) {
            // Group detail view
            val groupName = state.selectedGroup ?: return@Scaffold
            val favs = state.groups[groupName] ?: emptyList()

            // Scroll to target favorite when navigating from chip bar
            LaunchedEffect(targetScrollFavName) {
                targetScrollFavName?.let { targetName ->
                    val index = favs.indexOfFirst { it.name == targetName }
                    if (index >= 0) {
                        groupDetailListState.animateScrollToItem(index + 1) // +1 for header
                    }
                    targetScrollFavName = null
                }
            }

            LazyColumn(
                state = groupDetailListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                item {
                    TextButton(
                        onClick = { showAddFavDialog = groupName },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Add favorite")
                    }
                    HorizontalDivider()
                }

                if (favs.isEmpty()) {
                    item {
                        Text(
                            text = "No favorites in this group",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                } else {
                    items(favs, key = { "fav_${groupName}_${it.name}" }) { fav ->
                        FavoriteItem(
                            favorite = fav,
                            isStarred = fav.attributes["starred"] == "true",
                            onClick = { onFavoriteClick(fav) },
                            onDelete = { showDeleteFavDialog = groupName to fav.name },
                            onRename = { showRenameFavDialog = groupName to fav.name },
                            onToggleStar = { viewModel.toggleStar(groupName, fav.name) }
                        )
                    }
                }
            }
        } else {
            // Group grid view with search
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Starred chip bar
                if (state.starredFavorites.isNotEmpty()) {
                    StarredChipBar(
                        starredFavorites = state.starredFavorites,
                        onChipClick = { groupName, fav ->
                            onDismiss()
                            onChipRouteTo(fav)
                        }
                    )
                }

                // Search bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = { Text("Search favorites") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Quick action: add current map location
                TextButton(
                    onClick = { showAddMapLocDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Add current map location")
                }

                if (state.searchQuery.isNotEmpty()) {
                    // Search results view
                    val searchResults = state.groups.mapValues { (_, favs) ->
                        favs.filter { it.name.contains(state.searchQuery, ignoreCase = true) }
                    }.filter { it.value.isNotEmpty() }

                    if (searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No favorites match your search",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            searchResults.forEach { (groupName, favs) ->
                                item(key = "search_header_$groupName") {
                                    Text(
                                        text = groupName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(favs, key = { "search_fav_${groupName}_${it.name}" }) { fav ->
                                    FavoriteItem(
                                        favorite = fav,
                                        isStarred = fav.attributes["starred"] == "true",
                                        onClick = { onFavoriteClick(fav) },
                                        onDelete = { showDeleteFavDialog = groupName to fav.name },
                                        onRename = { showRenameFavDialog = groupName to fav.name },
                                        onToggleStar = { viewModel.toggleStar(groupName, fav.name) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Group grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.groups.keys.toList(), key = { "group_$it" }) { groupName ->
                            GroupCard(
                                groupName = groupName,
                                favCount = state.groups[groupName]?.size ?: 0,
                                colorHex = state.groupColors[groupName],
                                onClick = { viewModel.selectGroup(groupName) },
                                onRename = { showRenameGroupDialog = groupName },
                                onDelete = { showDeleteGroupDialog = groupName },
                                onSetColor = { showColorPickerGroup = groupName }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Dialogs ----

    if (showAddGroupDialog) {
        TextFieldDialog(
            title = "New Group",
            label = "Group name",
            onConfirm = { name ->
                viewModel.addGroup(name)
                showAddGroupDialog = false
            },
            onDismiss = { showAddGroupDialog = false }
        )
    }

    showDeleteGroupDialog?.let { groupName ->
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = null },
            title = { Text("Delete group?") },
            text = { Text("Delete \"$groupName\" and all its favorites?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupName)
                    showDeleteGroupDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showRenameGroupDialog?.let { groupName ->
        TextFieldDialog(
            title = "Rename Group",
            label = "New name",
            initialValue = groupName,
            onConfirm = { newName ->
                if (newName.isNotEmpty() && newName != groupName) {
                    viewModel.renameGroup(groupName, newName)
                }
                showRenameGroupDialog = null
            },
            onDismiss = { showRenameGroupDialog = null }
        )
    }

    showAddFavDialog?.let { groupName ->
        AddFavoriteDialog(
            groupName = groupName,
            onConfirm = { name, lat, lon ->
                viewModel.addFavorite(groupName, name, lat, lon)
                showAddFavDialog = null
            },
            onDismiss = { showAddFavDialog = null }
        )
    }

    showDeleteFavDialog?.let { (groupName, favName) ->
        AlertDialog(
            onDismissRequest = { showDeleteFavDialog = null },
            title = { Text("Delete favorite?") },
            text = { Text("Delete \"$favName\" from \"$groupName\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFavorite(groupName, favName)
                    showDeleteFavDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFavDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showRenameFavDialog?.let { (groupName, oldName) ->
        TextFieldDialog(
            title = "Rename Favorite",
            label = "New name",
            initialValue = oldName,
            onConfirm = { newName ->
                if (newName.isNotEmpty() && newName != oldName) {
                    viewModel.renameFavorite(groupName, oldName, newName)
                }
                showRenameFavDialog = null
            },
            onDismiss = { showRenameFavDialog = null }
        )
    }

    showColorPickerGroup?.let { groupName ->
        ColorPickerDialog(
            currentColor = state.groupColors[groupName],
            onColorSelected = { colorHex ->
                viewModel.setGroupColor(groupName, colorHex)
                showColorPickerGroup = null
            },
            onDismiss = { showColorPickerGroup = null }
        )
    }

    if (showAddMapLocDialog) {
        AddFavoriteDialog(
            groupName = "New location",
            initialLat = mapCenterLat,
            initialLon = mapCenterLon,
            onConfirm = { name, lat, lon ->
                val group = state.groups.keys.firstOrNull() ?: "Favorites"
                viewModel.addFavorite(group, name, lat, lon)
                showAddMapLocDialog = false
            },
            onDismiss = { showAddMapLocDialog = false }
        )
    }
}

@Composable
private fun GroupCard(
    groupName: String,
    favCount: Int,
    colorHex: String? = null,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetColor: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    val cardColor = colorHex?.let {
        try {
            Color(android.graphics.Color.parseColor(it))
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Gradient overlay when color assigned
            if (cardColor != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    cardColor.copy(alpha = 0.18f),
                                    cardColor.copy(alpha = 0.06f)
                                )
                            )
                        )
                )
            }
            // Content
            Box(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$favCount favorite${if (favCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Menu button in top-right corner
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Group options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Set Color") },
                        onClick = {
                            showMenu = false
                            onSetColor()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Create, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun FavoriteItem(
    favorite: FavoriteLocation,
    isStarred: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onToggleStar: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = favorite.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "%.5f, %.5f".format(favorite.lat, favorite.lon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleStar) {
            Icon(
                imageVector = if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isStarred) "Unstar" else "Star",
                tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Create, contentDescription = "Rename")
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    label: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotEmpty()
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StarredChipBar(
    starredFavorites: List<Pair<String, FavoriteLocation>>,
    onChipClick: (groupName: String, fav: FavoriteLocation) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(starredFavorites, key = { "${it.first}_${it.second.name}" }) { (groupName, fav) ->
            FilterChip(
                selected = false,
                onClick = { onChipClick(groupName, fav) },
                label = {
                    Column {
                        Text(
                            text = fav.name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    }
}

/** Predefined Material color palette for group color assignment. */
private val colorPalette = listOf(
    "#F44336", // Red
    "#E91E63", // Pink
    "#9C27B0", // Purple
    "#673AB7", // Deep Purple
    "#3F51B5", // Indigo
    "#2196F3", // Blue
    "#03A9F4", // Light Blue
    "#009688", // Teal
    "#4CAF50", // Green
    "#8BC34A", // Light Green
    "#FF9800", // Orange
    "#FF5722", // Deep Orange
    "#795548", // Brown
    "#607D8B", // Blue Grey
)

@Composable
private fun ColorPickerDialog(
    currentColor: String?,
    onColorSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Color") },
        text = {
            Column {
                if (currentColor != null) {
                    TextButton(
                        onClick = {
                            onColorSelected(null)
                            onDismiss()
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text("Remove color")
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(colorPalette, key = { it }) { hex ->
                        val isSelected = hex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onColorSelected(selectedColor)
                    onDismiss()
                },
                enabled = selectedColor != null
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddFavoriteDialog(
    groupName: String,
    initialLat: Double = 0.0,
    initialLon: Double = 0.0,
    onConfirm: (name: String, lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var latText by remember {
        mutableStateOf(if (initialLat != 0.0) "%.5f".format(initialLat) else "")
    }
    var lonText by remember {
        mutableStateOf(if (initialLon != 0.0) "%.5f".format(initialLon) else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Favorite to \"$groupName\"") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    if (name.isNotEmpty() && lat != null && lon != null) {
                        onConfirm(name, lat, lon)
                    }
                },
                enabled = name.isNotEmpty() && latText.toDoubleOrNull() != null && lonText.toDoubleOrNull() != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
