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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.AvailableMapEntry
import com.naviveylin.core.mapsource.MapSource
import com.naviveylin.data.MapSourceSwitchPlan
import com.naviveylin.R

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
    val availableTree = remember(
        availableEntries, expandedDirs, searchQuery, uiState.downloadingNames,
        uiState.repositoryRegionLabels
    ) {
        val filtered = if (searchQuery.isBlank()) {
            availableEntries
        } else {
            availableEntries.filter { entry ->
                entry.name.contains(searchQuery, ignoreCase = true) ||
                entry.path.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
        buildTreeItems(filtered, expandedDirs, uiState.repositoryRegionLabels)
    }

    // Refresh installed maps when screen opens, and read the offered map sources
    LaunchedEffect(Unit) {
        viewModel.refreshInstalledMaps()
        viewModel.refreshSources()
    }

    // A source change makes the basemap section's state stale: it must probe the newly active source
    // instead of showing the previous one's basemap (found on device 2026-10-09; spec
    // `basemap-discovery` — "Probe follows the source, not the stale one").
    LaunchedEffect(uiState.activeSource) {
        basemapViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // Map source selector + refresh
            item {
                SourceSelector(
                    state = uiState,
                    onSelect = { source -> viewModel.selectSource(source) },
                    onUrlChange = { url -> viewModel.updateRepositoryUrlDraft(url) },
                    onTest = { viewModel.testRepositoryUrl() },
                    onRefresh = {
                        if (uiState.activeSource.isRepository) {
                            viewModel.refreshRepositoryRegions()
                        } else {
                            viewModel.refreshAvailableMaps()
                        }
                    }
                )
            }

            // Loading indicator (top of content area, below provider row)
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

            // Search field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_maps)) },
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

            // World basemap section (basemap-ui spec)
            item(key = "basemap-section") {
                BasemapSection(viewModel = basemapViewModel)
            }

            // Installed maps section (hidden while searching)
            if (searchQuery.isBlank() && installedEntries.isNotEmpty()) {
                item(key = "installed-header") {
                    Text(
                        text = stringResource(R.string.installed_maps),
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
                        installedSourceName = { entry -> viewModel.installedMapSourceName(entry.name) },
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
                        text = stringResource(R.string.available_maps),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                        installedSourceName = { entry -> viewModel.installedMapSourceName(entry.name) },
                        leafState = { entry -> viewModel.leafStateOf(entry) },
                        onToggleDir = { dirKey ->
                            val toggle = toggleExpandedDirs(expandedDirs, dirKey)
                            expandedDirs = toggle.expanded
                            // Expanding a region reads the metadata of the leaves it reveals
                            // (spec `map-repository-source` — "Database metadata is fetched on
                            // demand per leaf"). Found on device 2026-10-09: this condition was
                            // inverted, so the probe only ever ran on a collapse — and the row
                            // quietly showed no size.
                            if (toggle.justExpanded) {
                                viewModel.probeLeavesUnder(dirKey)
                            }
                        },
                        onLeafProbe = { entry ->
                            // Selecting a repository leaf reads its metadata when it was not read yet.
                            viewModel.probeLeaf(entry.serverDirectory.orEmpty().split("/"))
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
            } else if (searchQuery.isBlank() && !uiState.isLoading && installedEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tap_refresh_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
        }

        // A source switch asks before it deletes anything (spec `map-source-selection`).
        uiState.pendingSourceSwitch?.let { plan ->
            SourceSwitchConfirmationDialog(
                plan = plan,
                onConfirm = { viewModel.confirmSourceSwitch() },
                onDismiss = { viewModel.cancelSourceSwitch() }
            )
        }
    }
}

// ── Previews ───────────────────────────────────────────────────

/** The source row with the repository selected and a tested URL. */
@Preview(showBackground = true)
@Composable
private fun SourceSelectorPreview() {
    SourceSelector(
        state = MapManagerUiState(
            sources = listOf(
                MapSource.BuiltInProvider,
                MapSource.repository("http://truenas.home.framstag.com:30123")
            ),
            activeSource = MapSource.repository("http://truenas.home.framstag.com:30123"),
            repositoryUrlDraft = "http://truenas.home.framstag.com:30123",
            sourceTestOutcome = SourceTestOutcome.Success(
                url = "http://truenas.home.framstag.com:30123/names.json",
                regionCount = 1,
                leafCount = 1
            )
        ),
        onSelect = {},
        onUrlChange = {},
        onTest = {},
        onRefresh = {}
    )
}

/** The confirmation a switch needs before it deletes the other source's data. */
@Preview(showBackground = true)
@Composable
private fun SourceSwitchConfirmationDialogPreview() {
    SourceSwitchConfirmationDialog(
        plan = MapSourceSwitchPlan(
            mapDirectories = listOf(
                java.nio.file.Paths.get("/maps/europe-germany-north-rhine-westphalia"),
                java.nio.file.Paths.get("/maps/iceland")
            ),
            mapBytes = 821_788_380L,
            basemapDirectory = java.nio.file.Paths.get("/maps/basemap"),
            basemapBytes = 90_500_000L
        ),
        onConfirm = {},
        onDismiss = {}
    )
}

// ── Source Selector ────────────────────────────────────────────

/**
 * The result of toggling a tree group row: the new expanded set and whether the row was *just* expanded
 * (which is when its leaves' metadata must be read).
 *
 * Extracted from the screen because the inline form of this condition was inverted once and nothing
 * noticed: the row simply showed no size, and only the device run made it visible (2026-10-09).
 */
data class ExpandedToggle(val expanded: Set<String>, val justExpanded: Boolean)

/** Toggle [dirKey] in [expanded]; [ExpandedToggle.justExpanded] is true only when it was closed. */
internal fun toggleExpandedDirs(expanded: Set<String>, dirKey: String): ExpandedToggle {
    val wasExpanded = dirKey in expanded
    return ExpandedToggle(
        expanded = if (wasExpanded) expanded - dirKey else expanded + dirKey,
        justExpanded = !wasExpanded
    )
}

/**
 * Keyboard options of the repository URL field.
 *
 * URL input, not prose: the platform's text input must not insert a space after a period or
 * capitalise a letter, which is what turns a hand-typed `http://10.0.2.2:30123` into
 * `http://10.0. 2. 2:30123` (spec `map-source-selection` — "The repository URL field is presented as
 * URL input with its format shown"). Declared as a value so a test can assert the configuration; that
 * a given input method honours it is a device question.
 */
internal val repositoryUrlKeyboardOptions = KeyboardOptions(
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Done
)

/**
 * Source selection: the offered sources, the repository's base URL with its test action, and the
 * refresh that fetches the active source's listing (spec `map-download-ui` — "Provider selection and
 * refresh").
 */
@Composable
internal fun SourceSelector(
    state: MapManagerUiState,
    onSelect: (MapSource) -> Unit,
    onUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.provider_label),
                style = MaterialTheme.typography.bodyMedium
            )
            state.sources.forEach { source ->
                FilterChip(
                    selected = source == state.activeSource,
                    onClick = { onSelect(source) },
                    label = {
                        Text(
                            text = if (source.isRepository) {
                                stringResource(R.string.source_repository_label)
                            } else {
                                stringResource(R.string.provider_karry)
                            }
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onRefresh,
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                Text(stringResource(R.string.refresh))
            }
        }

        if (state.activeSource.isRepository || state.repositoryUrlDraft.isNotBlank() ||
            state.repositoryUrlRevealed
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.repositoryUrlDraft,
                    onValueChange = onUrlChange,
                    label = { Text(stringResource(R.string.repository_url_label)) },
                    // URL input, not prose: see `repositoryUrlKeyboardOptions`.
                    keyboardOptions = repositoryUrlKeyboardOptions,
                    singleLine = true,
                    enabled = !state.isTestingSource,
                    supportingText = { Text(stringResource(R.string.repository_url_format_hint)) },
                    modifier = Modifier.weight(1f).testTag("repository-url-field")
                )
                Button(
                    onClick = onTest,
                    enabled = !state.isTestingSource && state.repositoryUrlDraft.isNotBlank(),
                    modifier = Modifier.testTag("repository-url-test")
                ) {
                    Text(stringResource(R.string.source_test_action))
                }
            }
            state.unencryptedUrl?.let { unencrypted ->
                Text(
                    text = stringResource(R.string.repository_url_unencrypted_notice, unencrypted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .testTag("repository-url-unencrypted-notice")
                )
            }
            state.sourceTestOutcome?.let { outcome ->
                Text(
                    text = outcome.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outcome is SourceTestOutcome.Success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** A user-actionable description of a test outcome (spec `map-source-selection`). */
@Composable
private fun SourceTestOutcome.describe(): String = when (this) {
    is SourceTestOutcome.Success ->
        stringResource(R.string.source_test_success, regionCount, leafCount)
    is SourceTestOutcome.Failure -> when (val reason = failure) {
        is com.naviveylin.core.mapsource.RepositoryFailure.UnsupportedSchema ->
            stringResource(R.string.source_test_unsupported_schema, url, reason.found ?: -1)
        is com.naviveylin.core.mapsource.RepositoryFailure.TransportFailed ->
            stringResource(R.string.source_test_unreachable, url, reason.cause.orEmpty())
        is com.naviveylin.core.mapsource.RepositoryFailure.CleartextBlocked ->
            stringResource(R.string.source_test_cleartext_blocked, url)
        is com.naviveylin.core.mapsource.RepositoryFailure.MalformedUrl ->
            stringResource(R.string.source_test_malformed_url, url)
        is com.naviveylin.core.mapsource.RepositoryFailure.HttpStatus ->
            stringResource(R.string.source_test_unreachable, url, reason.status.toString())
        else -> stringResource(R.string.source_test_not_a_repository, url)
    }
}

// ── Source switch confirmation ─────────────────────────────────

/**
 * The confirmation a source switch needs before it deletes anything: how many maps and how many
 * bytes disappear, the basemap included (spec `map-source-selection`; spec `map-download-ui` —
 * "Switching source asks before deleting").
 */
@Composable
internal fun SourceSwitchConfirmationDialog(
    plan: MapSourceSwitchPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("source-switch-dialog"),
        title = { Text(stringResource(R.string.source_switch_dialog_title)) },
        text = {
            Text(
                stringResource(
                    R.string.source_switch_dialog_body,
                    plan.mapCount,
                    "%.1f MB".format(plan.totalBytes / (1024.0 * 1024.0))
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.source_switch_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
                text = pluralStringResource(
                    R.plurals.active_downloads,
                    downloads.size + if (basemapDownloading) 1 else 0,
                    downloads.size + if (basemapDownloading) 1 else 0
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
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
                text = stringResource(R.string.world_basemap),
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
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelSmall)
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
                    Text(stringResource(R.string.ok), style = MaterialTheme.typography.labelSmall)
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
                    Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelSmall)
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
    expandedDirs: Set<String> = emptySet(),
    regionLabels: Map<String, String> = emptyMap()
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
                // A repository's path segment is its identifier; the index's localized name is shown
                // when it knows one (spec `map-repository-source` — "Tree rendered with localized
                // names"). A provider's segment is already the name it publishes.
                label = regionLabels[dirKey] ?: dirName,
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

/**
 * The status line of an available row that is not installed: for a repository leaf, what the probe of
 * its own version slot found (spec `map-repository-source` — "Leaf published for another database
 * version").
 */
@Composable
private fun leafStatusText(entry: AvailableMapEntry, state: LeafMetadataState?): String? = when (state) {
    null -> null
    LeafMetadataState.Probing -> stringResource(R.string.leaf_metadata_probing)
    is LeafMetadataState.Loaded ->
        // The size is the entry's own (shown above this line); the version is what the metadata adds.
        stringResource(R.string.leaf_map_version, state.metadata.typeConfigVersion)
    is LeafMetadataState.NotPublished ->
        stringResource(R.string.leaf_not_published, state.databaseFormatVersion)
    is LeafMetadataState.Failed -> stringResource(R.string.leaf_metadata_failed)
}

@Composable
private fun TreeItemRow(
    item: TreeItemData,
    isInstalled: (AvailableMapEntry) -> Boolean,
    isDownloading: (AvailableMapEntry) -> Boolean,
    downloadState: (AvailableMapEntry) -> MapEntryState?,
    progress: (AvailableMapEntry) -> Int,
    onToggleDir: (String) -> Unit,
    onDownload: (AvailableMapEntry) -> Unit,
    installedSourceName: (AvailableMapEntry) -> String = { "" },
    leafState: (AvailableMapEntry) -> LeafMetadataState? = { null },
    /** Called when a repository leaf is tapped to read its metadata (spec `map-repository-source`). */
    onLeafProbe: (AvailableMapEntry) -> Unit = {},
    onCancel: (AvailableMapEntry) -> Unit,
    onDelete: (AvailableMapEntry) -> Unit,
    onMapSelected: (AvailableMapEntry) -> Unit = {}
) {
    val indent = (item.depth * 24).dp
    val runWithNotificationPermission = rememberNotificationPermissionLauncher()

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
                imageVector = if (item.isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (item.isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
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
                    when {
                        installed -> Modifier.clickable { onMapSelected(entry) }
                        // An available repository leaf is tappable to read its metadata; the download
                        // button stays the action that installs it.
                        entry.serverDirectory != null -> Modifier.clickable { onLeafProbe(entry) }
                        else -> Modifier
                    }
                )
                .padding(start = indent, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State icon
            when {
                installed -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.installed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                downloading -> CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = stringResource(R.string.available),
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
                        text = stringResource(
                            R.string.size_unit_mb,
                            "%.1f".format(entry.size / (1024.0 * 1024.0))
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (installed) {
                    // Which source installed this map, so a user running both a provider and a
                    // repository can tell the two apart (spec `map-download-ui`).
                    Text(
                        text = stringResource(R.string.installed_from_source, installedSourceName(entry)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    leafStatusText(entry, leafState(entry))?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        Text(stringResource(R.string.cancel))
                    }
                }
                installed -> {
                    OutlinedButton(onClick = { onDelete(entry) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                else -> {
                    Button(onClick = { runWithNotificationPermission { onDownload(entry) } }) {
                        Text(stringResource(R.string.download))
                    }
                }
            }
        }
    }
}
