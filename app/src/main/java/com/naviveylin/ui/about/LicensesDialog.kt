package com.naviveylin.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.naviveylin.R
import com.naviveylin.ui.attribution.openUrl

/**
 * Lists the third-party components bundled in this build with their licenses,
 * and shows one component's full license text.
 *
 * The data is generated at build time from the same SBOM the license gate
 * validates, so this screen cannot drift from what the build permits. A license
 * whose text is not distributed with the application is offered as a link to its
 * canonical terms instead of an empty pane (`licenses/license-policy.json`,
 * `licenseRefs`).
 */
@Composable
fun LicensesDialog(
    onDismiss: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LicensesDialogContent(
        state = state,
        onDismiss = onDismiss,
        onSelect = viewModel::select,
        onBackToList = viewModel::clearSelection,
    )
}

/** Stateless presentation, so the screen can be previewed and tested directly. */
@Composable
fun LicensesDialogContent(
    state: LicensesUiState,
    onDismiss: () -> Unit,
    onSelect: (LicenseComponent) -> Unit,
    onBackToList: () -> Unit,
) {
    when (state) {
        is LicensesUiState.Loading -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.licenses_title)) },
            text = { Text(stringResource(R.string.licenses_loading)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        )

        is LicensesUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.licenses_title)) },
            text = {
                Text(stringResource(R.string.licenses_unavailable, state.message))
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        )

        is LicensesUiState.Content -> {
            val selected = state.selected
            if (selected == null) {
                LicenseListDialog(state, onSelect, onDismiss)
            } else {
                LicenseDetailDialog(selected, onBack = onBackToList, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun LicenseListDialog(
    state: LicensesUiState.Content,
    onSelect: (LicenseComponent) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                state.appVersion?.let {
                    Text(
                        text = stringResource(R.string.version_format, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                state.components.forEach { component ->
                    LicenseRow(component = component, onClick = { onSelect(component) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun LicenseRow(component: LicenseComponent, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = component.qualifiedName,
            style = MaterialTheme.typography.bodyMedium
        )
        // One subtitle string: identifier, version, and a marker for build-time
        // tooling that ships nothing.
        val subtitleParts = buildList {
            add(component.identifier)
            component.version?.let { add(it) }
            if (!component.isDistributed) add(stringResource(R.string.licenses_build_time_only))
        }
        Text(
            text = subtitleParts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        component.note?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun LicenseDetailDialog(
    selected: SelectedLicense,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val component = selected.component
    val linkUrl = selected.linkUrl

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = component.qualifiedName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = component.licenseName ?: component.identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    linkUrl != null -> {
                        Text(
                            text = stringResource(R.string.licenses_link_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { openUrl(context, linkUrl) }) {
                            Text(linkUrl)
                        }
                    }
                    selected.text != null -> Text(
                        text = selected.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    else -> Text(stringResource(R.string.licenses_loading))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.licenses_back)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun LicensesDialogPreview() {
    LicensesDialogContent(
        state = LicensesUiState.Content(
            appVersion = "2026-09-13-1",
            components = listOf(
                LicenseComponent(
                    name = "expat",
                    version = "2.8.2",
                    identifier = "MIT",
                    textFile = "MIT.txt"
                ),
                LicenseComponent(
                    name = "play-services-location",
                    group = "com.google.android.gms",
                    version = "21.1.0",
                    identifier = "LicenseRef-AndroidSDK",
                    licenseName = "Android Software Development Kit License",
                    licenseUrl = "https://developer.android.com/studio/terms.html"
                ),
                LicenseComponent(
                    name = "protobuf",
                    version = "6.33.4",
                    identifier = "BSD-3-Clause",
                    scope = "buildTimeOnly"
                )
            )
        ),
        onDismiss = {},
        onSelect = {},
        onBackToList = {}
    )
}
