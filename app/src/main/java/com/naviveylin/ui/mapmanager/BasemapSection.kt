package com.naviveylin.ui.mapmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.framstag.libosmscout.client.BasemapManager

/**
 * World basemap section for the map manager screen: status, download/update,
 * delete, progress, and error handling (basemap-ui spec).
 */
@Composable
fun BasemapSection(
    viewModel: BasemapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "World Basemap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (state.isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        when {
            state.isDownloading -> {
                // Download in progress
                Text(
                    text = "Downloading… ${state.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.weight(1f).height(6.dp)
                    )
                    TextButton(onClick = { viewModel.cancel() }) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            state.error != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            state.installedInfo != null -> {
                // Installed: size/version + update/delete controls
                val installedInfo = state.installedInfo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Installed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Installed (${installedInfo!!.sizeHuman})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${installedInfo!!.fileCount} files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.updateAvailable) {
                        Button(onClick = { viewModel.update() }) {
                            Text("Update")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.delete() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete basemap",
                            modifier = Modifier.width(16.dp).height(16.dp)
                        )
                        Text("Delete")
                    }
                }
            }

            state.availability == BasemapAvailability.Available -> {
                // Available on server, not installed: download (variant selection)
                if (state.variants.size > 1) {
                    Text(
                        text = "Available on server — choose a variant:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.variants.forEach { archive ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = archive.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { viewModel.download(archive) }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.width(16.dp).height(16.dp)
                                )
                                Text("Download")
                            }
                        }
                    }
                } else {
                    val archive = state.variants.firstOrNull()
                    if (archive != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = archive.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { viewModel.download(archive) }) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.width(16.dp).height(16.dp)
                                )
                                Text("Download Basemap")
                            }
                        }
                    }
                }
            }

            state.availability == BasemapAvailability.Unavailable -> {
                // Optional: no error shown, just subtle status
                Text(
                    text = "Basemap unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}
