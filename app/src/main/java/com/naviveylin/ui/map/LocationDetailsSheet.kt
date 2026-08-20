package com.naviveylin.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.DescriptionEntry
import com.framstag.libosmscout.client.LocationEntry
import com.framstag.libosmscout.client.OSMScoutClient
import com.framstag.libosmscout.client.ObjectDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Long-press labels are formatted coordinates ("%.5f, %.5f") — never an address. */
private val COORDINATE_LABEL_REGEX = Regex("""-?\d+\.\d+,\s*-?\d+\.\d+""")

/**
 * Full-screen details view for a selected object (spec: enhanced-details-sheet).
 *
 * Replaces the former bottom sheet: fills the entire screen and shows, top to
 * bottom, the object name, an interactive mini map of the surroundings, and a
 * scrollable description list (coordinates, admin region, structured sections,
 * actions). Dismissed via the system back gesture (wired by the caller) —
 * no map content remains visible behind it.
 *
 * @param entry selected location (label, coordinates, admin region hierarchy)
 * @param client shared OSMScoutClient for the embedded [MiniMap]
 * @param initialMag magnification the mini map starts at (main map's, clamped)
 * @param objectDescription structured native description, null when unavailable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDetailsDialog(
    entry: LocationEntry,
    client: OSMScoutClient,
    initialMag: Int,
    objectDescription: ObjectDescription? = null,
    isFavorite: Boolean,
    groupNames: List<String>,
    /** Current position for the mini map (blue dot); null when no GPS fix. */
    currentPosition: Pair<Double, Double>? = null,
    onAddToFavorites: (groupName: String, favName: String, isNewGroup: Boolean) -> Unit,
    onRemoveFromFavorites: () -> Unit,
    onRouteToLocation: (() -> Unit)? = null,
    onShowOnMap: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showGroupPicker by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf(groupNames.firstOrNull() ?: "") }
    var newGroupName by remember { mutableStateOf("") }
    var favName by remember { mutableStateOf(entry.label) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Reverse-lookup the address at the object's position via the location
    // index — covers objects whose own tags lack addr:street (the index knows
    // the street the house number belongs to). Returns
    // [street, houseNumber, adminRegion, postalArea].
    var resolvedAddress by remember { mutableStateOf<Array<String>?>(null) }
    LaunchedEffect(entry.lat, entry.lon) {
        resolvedAddress = withContext(Dispatchers.Default) {
            try {
                client.getAddressAt(entry.lat, entry.lon)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Derived display data from the native description + reverse lookup:
    // - fullAddress: street + house number + postal code + city
    // - area: admin region hierarchy (Area list entry)
    // - title: object name if present, else the full address, else the label
    val (title, fullAddress, area) = remember(entry, objectDescription, resolvedAddress) {
        val entries = objectDescription?.entries.orEmpty()
        val locationEntries = entries.filter { it.sectionKey == "Location" }
        val name = entries.firstOrNull {
            it.sectionKey == "General" && it.labelKey == "Name"
        }?.value?.takeIf { it.isNotBlank() }
            ?: entry.name?.takeIf { it.isNotBlank() }
        val label = entry.label?.takeIf { it.isNotBlank() && it != "(unnamed)" }
        // A label that is not the object name, not coordinates, and contains a
        // digit is an address label (e.g. "Hauptstraße 12") — usable as street.
        val labelIsCoordinates = label?.matches(COORDINATE_LABEL_REGEX) == true
        val labelIsName = name != null && label == name
        val descStreet = locationEntries.firstOrNull { it.labelKey == "Location" }?.value
        val reverseStreet = resolvedAddress?.getOrNull(0)?.takeIf { it.isNotBlank() }
        val labelStreet = if (descStreet.isNullOrBlank() && reverseStreet.isNullOrBlank() &&
            label != null && !labelIsCoordinates && !labelIsName && label.any { it.isDigit() }
        ) {
            label
        } else {
            null
        }
        val houseNr = locationEntries.firstOrNull { it.labelKey == "Address" }?.value
            ?: resolvedAddress?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val streetAndNumber = when {
            !descStreet.isNullOrBlank() && !houseNr.isNullOrBlank() -> "$descStreet $houseNr"
            reverseStreet != null && !houseNr.isNullOrBlank() -> "$reverseStreet $houseNr"
            labelStreet != null -> labelStreet
            !houseNr.isNullOrBlank() -> houseNr
            !descStreet.isNullOrBlank() -> descStreet
            else -> null
        }
        val reverseRegion = resolvedAddress?.getOrNull(2)?.takeIf { it.isNotBlank() }
        val reversePostal = resolvedAddress?.getOrNull(3)?.takeIf { it.isNotBlank() }
        val postal = reversePostal ?: entry.postalArea?.takeIf { it.isNotBlank() }
        val isIn = entries.firstOrNull {
            it.sectionKey == "Location" &&
                it.subsectionKey == "AdminLevel" &&
                it.labelKey == "IsIn"
        }?.value
        val area = entry.adminRegionHierarchy?.takeIf { it.isNotBlank() }
            ?: reverseRegion
            ?: isIn
            ?: postal
        // City for the address line: deepest region name (reverse lookup gives
        // the address's own region; otherwise the last hierarchy segment).
        val city = reverseRegion
            ?: area?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: isIn
        val address = if (streetAndNumber != null) {
            val suffix = listOfNotNull(postal, city)
                .filter { it != streetAndNumber }
                .distinct()
                .joinToString(" ")
            if (suffix.isNotBlank()) "$streetAndNumber, $suffix" else streetAndNumber
        } else {
            null
        }
        val title = name
            ?: address
            ?: label
            ?: entry.label
        Triple(title, address, area)
    }

    // System back (edge swipe / button, incl. predictive back on API 33+)
    // closes the dialog and returns to the previous view. Composed only while
    // the dialog is open, so it wins over the map screen's back handlers.
    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Object name (top of the dialog): the object's name if it has one,
            // otherwise its address, otherwise the search label.
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            )

            // Interactive mini map of the object's surroundings
            MiniMap(
                client = client,
                lat = entry.lat,
                lon = entry.lon,
                initialMag = initialMag,
                currentPosition = currentPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            // Scrollable description content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Coordinates
                Text(
                    text = "%.5f, %.5f".format(entry.lat, entry.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Area as a structured list entry (admin region hierarchy, or the
                // description's admin-level "IsIn" for results without one)
                if (area != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Area:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(120.dp)
                        )
                        Text(
                            text = area,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Structured description sections (native DescriptionService
                // entries). The street ("Location") and house number ("Address")
                // entries are merged into one full-address row.
                val entries = objectDescription?.entries
                if (entries != null && entries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Merge street + house number into a single "Address" row;
                    // drop the standalone street row when merged.
                    val displayEntries = if (fullAddress != null) {
                        entries.map { e ->
                            if (e.sectionKey == "Location" && e.labelKey == "Address") {
                                DescriptionEntry().apply {
                                    sectionKey = e.sectionKey
                                    subsectionKey = e.subsectionKey
                                    hasIndex = e.hasIndex
                                    index = e.index
                                    labelKey = "Address"
                                    value = fullAddress
                                }
                            } else {
                                e
                            }
                        }.filterNot { it.sectionKey == "Location" && it.labelKey == "Location" }
                    } else {
                        entries
                    }

                    // Group entries by sectionKey, preserving order
                    val sections = mutableListOf<Pair<String, List<DescriptionEntry>>>()
                    val sectionOrder = mutableListOf<String>()
                    val sectionMap = linkedMapOf<String, MutableList<DescriptionEntry>>()
                    for (entry in displayEntries) {
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

                // Show on map action (shown when the details dialog was opened
                // from POI search): closing via this selective action also keeps
                // the POI sheet closed (spec: show action closes both dialogs).
                if (onShowOnMap != null) {
                    OutlinedButton(
                        onClick = {
                            onShowOnMap()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Show")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
