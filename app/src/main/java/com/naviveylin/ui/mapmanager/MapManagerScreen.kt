package com.naviveylin.ui.mapmanager

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.AvailableMapEntry

/**
 * Unified map management screen combining available maps browsing,
 * active download progress, and installed map management in one view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapManagerScreen(
    onBack: () -> Unit,
    onMapSelected: (String) -> Unit = {},
    viewModel: MapManagerViewModel = hiltViewModel(),
    basemapViewModel: BasemapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val basemapState by basemapViewModel.uiState.collectAsState()
    var expandedDirs by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    // Separate installed vs available entries
    val installedEntries = remember(uiState.availableEntries, uiState.installedMapPaths) {
        uiState.availableEntries.filter { entry ->
            val targetPath = viewModel.getMapPath(entry.name)
            targetPath in uiState.installedMapPaths
        }
    }
    val availableEntries = remember(uiState.availableEntries, uiState.installedMapPaths) {
        uiState.availableEntries.filter { entry ->
            val targetPath = viewModel.getMapPath(entry.name)
            targetPath !in uiState.installedMapPaths
        }
    }

    val installedTree = remember(installedEntries, expandedDirs) {
        installedEntries.map { entry ->
            TreeItemData(
                id = entry.name,
                label = entry.name,
                depth = 0,
                entry = entry,
                isDirectory = false
            )
        }
    }
    val availableTree = remember(availableEntries, expandedDirs, searchQuery, uiState.downloadingNames) {
        val filtered = if (searchQuery.isBlank()) {
            availableEntries
        } else {
            availableEntries.filter { entry ->
                entry.name.contains(searchQuery, ignoreCase = true) ||
                entry.path.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
        buildTreeItems(filtered, expandedDirs)
    }

    // Refresh installed maps when screen opens
    LaunchedEffect(Unit) {
        viewModel.refreshInstalledMaps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Provider selector + refresh
            item {
                ProviderSelector(
                    onRefresh = { viewModel.refreshAvailableMaps() },
                    isLoading = uiState.isLoading
                )
            }

            // World basemap section (basemap-ui spec)
            item(key = "basemap-section") {
                BasemapSection(viewModel = basemapViewModel)
            }

            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search maps…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            // Error banner
            uiState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Active downloads section
            if (uiState.activeDownloads.isNotEmpty() || basemapState.isDownloading) {
                item(key = "active-downloads") {
                    ActiveDownloadsSection(
                        downloads = uiState.activeDownloads,
                        progressMap = uiState.progressMap,
                        onCancel = { viewModel.cancelDownload(it.entry.name) },
                        onDismissError = { viewModel.dismissError(it.entry.name) },
                        basemapDownloading = basemapState.isDownloading,
                        basemapProgress = basemapState.progress,
                        onCancelBasemap = { basemapViewModel.cancel() }
                    )
                }
            }

            // Loading indicator
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Installed maps section (always on top)
            if (installedEntries.isNotEmpty()) {
                item(key = "installed-header") {
                    Text(
                        text = "Installed Maps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(installedTree, key = { "inst-${it.id}" }) { item ->
                    TreeItemRow(
                        item = item,
                        isInstalled = { true },
                        isDownloading = { false },
                        downloadState = { null },
                        progress = { 0 },
                        onToggleDir = { dirKey ->
                            expandedDirs = if (dirKey in expandedDirs) {
                                expandedDirs - dirKey
                            } else {
                                expandedDirs + dirKey
                            }
                        },
                        onDownload = { },
                        onCancel = { },
                        onDelete = { viewModel.deleteMap(it.name) },
                        onMapSelected = { entry ->
                            val path = viewModel.getMapPath(entry.name)
                            onMapSelected(path)
                        }
                    )
                }
                item(key = "installed-divider") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            // Available maps section
            if (availableEntries.isNotEmpty()) {
                item(key = "available-header") {
                    Text(
                        text = "Available Maps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(availableTree, key = { "avail-${it.id}" }) { item ->
                    TreeItemRow(
                        item = item,
                        isInstalled = { entry -> viewModel.isMapInstalled(entry) },
                        isDownloading = { entry -> viewModel.isMapDownloading(entry) },
                        downloadState = { entry -> viewModel.getDownloadState(entry) },
                        progress = { entry -> uiState.progressMap[entry.name] ?: 0 },
                        onToggleDir = { dirKey ->
                            expandedDirs = if (dirKey in expandedDirs) {
                                expandedDirs - dirKey
                            } else {
                                expandedDirs + dirKey
                            }
                        },
                        onDownload = { entry -> viewModel.downloadMap(entry) },
                        onCancel = { viewModel.cancelDownload(it.name) },
                        onDelete = { viewModel.deleteMap(it.name) },
                        onMapSelected = { entry ->
                            val path = viewModel.getMapPath(entry.name)
                            onMapSelected(path)
                        }
                    )
                }
            } else if (!uiState.isLoading && installedEntries.isEmpty()) {
                item {
                    Text(
                        text = "Tap Refresh to load available maps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }
    }
}

// ── Provider Selector ──────────────────────────────────────────

@Composable
private fun ProviderSelector(
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Provider:",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "karry.cz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onRefresh,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.width(16.dp).height(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
            Text("Refresh")
        }
    }
}

// ── Active Downloads Section ───────────────────────────────────

@Composable
private fun ActiveDownloadsSection(
    downloads: List<MapEntryState>,
    progressMap: Map<String, Int>,
    onCancel: (MapEntryState) -> Unit,
    onDismissError: (MapEntryState) -> Unit,
    basemapDownloading: Boolean = false,
    basemapProgress: Int = 0,
    onCancelBasemap: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Downloads (${downloads.size + if (basemapDownloading) 1 else 0})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (dl in downloads) {
                    key(dl.entry.name) {
                        ActiveDownloadRow(
                            dl = dl,
                            progress = progressMap[dl.entry.name] ?: dl.progress,
                            onCancel = { onCancel(dl) },
                            onDismissError = { onDismissError(dl) }
                        )
                    }
                }
                if (basemapDownloading) {
                    key("basemap-download") {
                        BasemapDownloadRow(
                            progress = basemapProgress,
                            onCancel = onCancelBasemap
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun BasemapDownloadRow(
    progress: Int,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "World Basemap",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.weight(1f).height(6.dp)
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    dl: MapEntryState,
    progress: Int,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    val isError = dl.downloadState == DownloadState.Error
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dl.entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!isError) {
                Text(
                    text = dl.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isError) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dl.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissError) {
                    Text("OK", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.weight(1f).height(6.dp)
                )
                TextButton(onClick = onCancel) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Tree Model ─────────────────────────────────────────────────

/** A flattened tree item for display in LazyColumn. */
data class TreeItemData(
    val id: String,
    val label: String,
    val depth: Int,
    val entry: AvailableMapEntry?,
    val isDirectory: Boolean,
    val isExpanded: Boolean = false
)

/** Build a flat list of tree items from the hierarchical AvailableMapEntry list. */
private fun buildTreeItems(
    entries: List<AvailableMapEntry>,
    expandedDirs: Set<String> = emptySet()
): List<TreeItemData> {
    val result = mutableListOf<TreeItemData>()

    // Group entries by their path
    val byPath = mutableMapOf<String, MutableList<AvailableMapEntry>>()
    for (e in entries) {
        val key = e.path.joinToString("/")
        byPath.getOrPut(key) { mutableListOf() }.add(e)
    }

    // Collect all unique path prefixes (potential directories)
    val allDirKeys = mutableSetOf<String>()
    for (e in entries) {
        var cum = ""
        for (seg in e.path) {
            cum = if (cum.isEmpty()) seg else "$cum/$seg"
            allDirKeys.add(cum)
        }
    }

    // Recursively add nodes starting from a given path key
    fun addNodes(parentKey: String, depth: Int) {
        // Add subdirectories (path segments that are direct children of parentKey)
        val prefix = if (parentKey.isEmpty()) "" else "$parentKey/"
        val childDirKeys = allDirKeys.filter { it.startsWith(prefix) && it != parentKey }
            .map { it.removePrefix(prefix) }
            .filter { !it.contains("/") }
            .sorted()

        for (dirName in childDirKeys) {
            val dirKey = if (parentKey.isEmpty()) dirName else "$parentKey/$dirName"
            val parentPath = if (parentKey.isEmpty()) emptyList() else parentKey.split("/")
            val dirEntry = AvailableMapEntry(dirName, parentPath, "")
            result.add(TreeItemData(
                id = "dir-$dirKey",
                label = dirName,
                depth = depth,
                entry = dirEntry,
                isDirectory = true,
                isExpanded = dirKey in expandedDirs
            ))
            if (dirKey in expandedDirs) {
                addNodes(dirKey, depth + 1)
            }
        }

        // Add leaf entries at this path level
        val leaves = byPath[parentKey]?.filter { !it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        for (leaf in leaves) {
            result.add(TreeItemData(
                id = "leaf-$parentKey/${leaf.name}",
                label = leaf.name,
                depth = depth,
                entry = leaf,
                isDirectory = false
            ))
        }
    }

    // Start from root
    addNodes("", 0)
    return result
}

// ── Tree Item Row ──────────────────────────────────────────────

@Composable
private fun TreeItemRow(
    item: TreeItemData,
    isInstalled: (AvailableMapEntry) -> Boolean,
    isDownloading: (AvailableMapEntry) -> Boolean,
    downloadState: (AvailableMapEntry) -> MapEntryState?,
    progress: (AvailableMapEntry) -> Int,
    onToggleDir: (String) -> Unit,
    onDownload: (AvailableMapEntry) -> Unit,
    onCancel: (AvailableMapEntry) -> Unit,
    onDelete: (AvailableMapEntry) -> Unit,
    onMapSelected: (AvailableMapEntry) -> Unit = {}
) {
    val indent = (item.depth * 24).dp

    if (item.isDirectory) {
        val dirKey = item.id.removePrefix("dir-")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleDir(dirKey) }
                .padding(start = indent, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = if (item.isExpanded) "Collapse" else "Expand",
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        val entry = item.entry ?: return
        val installed = isInstalled(entry)
        val downloading = isDownloading(entry)
        val dlState = downloadState(entry)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (installed) Modifier.clickable { onMapSelected(entry) }
                    else Modifier
                )
                .padding(start = indent, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State icon
            when {
                installed -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Installed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                downloading -> CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Available",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Map name + size
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.size > 0 && !installed) {
                    Text(
                        text = "%.1f MB".format(entry.size / (1024.0 * 1024.0)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress or action button
            when {
                downloading -> {
                    val pct = progress(entry)
                    Text(
                        text = if (pct > 0) "$pct%" else dlState?.statusText ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    TextButton(onClick = { onCancel(entry) }) {
                        Text("Cancel")
                    }
                }
                installed -> {
                    OutlinedButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Delete")
                    }
                }
                else -> {
                    Button(onClick = { onDownload(entry) }) {
                        Text("Download")
                    }
                }
            }
        }
    }
}
