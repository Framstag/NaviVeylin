package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.ObjectDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailsSheet(
    entry: LocationEntry,
    objectDescription: ObjectDescription? = null,
    isFavorite: Boolean,
    groupNames: List<String>,
    onAddToFavorites: (groupName: String, favName: String, isNewGroup: Boolean) -> Unit,
    onRemoveFromFavorites: () -> Unit,
    onRouteToLocation: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // Full expansion for AAOS compatibility (short screens)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGroupPicker by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf(groupNames.firstOrNull() ?: "") }
    var newGroupName by remember { mutableStateOf("") }
    var favName by remember { mutableStateOf(entry.label) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Location name
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleLarge
            )

            // Admin region
            if (entry.adminRegionHierarchy != null && entry.adminRegionHierarchy.isNotEmpty()) {
                Text(
                    text = entry.adminRegionHierarchy,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Coordinates
            Text(
                text = "%.5f, %.5f".format(entry.lat, entry.lon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Structured description sections
            val entries = objectDescription?.entries
            if (entries != null && entries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Group entries by sectionKey, preserving order
                val sections = mutableListOf<Pair<String, List<DescriptionEntry>>>()
                val sectionOrder = mutableListOf<String>()
                val sectionMap = linkedMapOf<String, MutableList<DescriptionEntry>>()
                for (entry in entries) {
                    val section = sectionMap.getOrPut(entry.sectionKey) { mutableListOf() }
                    if (section.isEmpty()) {
                        sectionOrder.add(entry.sectionKey)
                    }
                    section.add(entry)
                }
                for (key in sectionOrder) {
                    sections.add(key to (sectionMap[key] ?: emptyList()))
                }

                for ((sectionKey, sectionEntries) in sections) {
                    // Section header
                    Text(
                        text = sectionKey,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    var currentSubsection: String? = null
                    var subsectionIndex = -1

                    for (descEntry in sectionEntries) {
                        // Subsection header (if different from current)
                        if (descEntry.subsectionKey.isNotEmpty() &&
                            descEntry.subsectionKey != currentSubsection
                        ) {
                            currentSubsection = descEntry.subsectionKey
                            subsectionIndex = if (descEntry.hasIndex) descEntry.index else -1
                            val subsectionLabel = if (subsectionIndex >= 0) {
                                "$currentSubsection $subsectionIndex"
                            } else {
                                currentSubsection
                            }
                            Text(
                                text = subsectionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp)
                            )
                        }

                        // Label/value row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (currentSubsection != null) Modifier.padding(start = 16.dp)
                                    else Modifier
                                )
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = descEntry.labelKey + ":",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(120.dp)
                            )
                            Text(
                                text = descEntry.value,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Route button
            if (onRouteToLocation != null) {
                Button(
                    onClick = {
                        onRouteToLocation()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Route")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isFavorite) {
                // Remove from favorites
                Button(
                    onClick = onRemoveFromFavorites,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Remove from Favorites")
                }
            } else if (!showGroupPicker) {
                // Add to favorites
                Button(
                    onClick = { showGroupPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Add to Favorites")
                }
            }

            if (showGroupPicker && !isFavorite) {
                // Favorite name
                OutlinedTextField(
                    value = favName,
                    onValueChange = { favName = it },
                    label = { Text("Favorite name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Group picker dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (newGroupName.isNotEmpty()) "New: $newGroupName" else selectedGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Group") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        groupNames.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedGroup = name
                                    newGroupName = ""
                                    dropdownExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("+ New group...") },
                            onClick = {
                                selectedGroup = ""
                                newGroupName = ""
                                dropdownExpanded = false
                            }
                        )
                    }
                }

                // New group name input (shown when "+ New group..." selected)
                if (selectedGroup.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("New group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showGroupPicker = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val group = if (newGroupName.isNotEmpty()) newGroupName else selectedGroup
                            if (group.length >= 2 && favName.isNotEmpty()) {
                                onAddToFavorites(group, favName, selectedGroup.isEmpty())
                                showGroupPicker = false
                            }
                        },
                        enabled = favName.isNotEmpty() && (selectedGroup.isNotEmpty() || newGroupName.length >= 2),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
