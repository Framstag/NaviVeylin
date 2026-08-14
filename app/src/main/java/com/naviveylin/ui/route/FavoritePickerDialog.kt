package com.naviveylin.ui.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.FavoriteLocation
import com.framstag.libosmscout.client.LocationEntry
import com.naviveylin.data.FavoriteRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritePickerDialog(
    favoriteRepository: FavoriteRepository,
    onFavoriteSelected: (LocationEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val favorites by favoriteRepository.favorites.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select Favorite",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (favorites.isEmpty()) {
                Text(
                    text = "No favorites yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    favorites.forEach { (groupName, favList) ->
                        // Group header
                        item {
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        // Favorites in group
                        items(favList) { fav ->
                            FavoriteItem(
                                fav = fav,
                                onClick = {
                                    val entry = LocationEntry().apply {
                                        label = fav.name
                                        lat = fav.lat
                                        lon = fav.lon
                                        matchQuality = "favorite"
                                    }
                                    onFavoriteSelected(entry)
                                    onDismiss()
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteItem(
    fav: FavoriteLocation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fav.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "%.5f, %.5f".format(fav.lat, fav.lon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
