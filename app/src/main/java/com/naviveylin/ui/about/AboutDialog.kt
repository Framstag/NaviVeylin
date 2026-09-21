package com.naviveylin.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naviveylin.BuildConfig
import com.naviveylin.R
import com.naviveylin.core.DiagnosticsLog
import com.naviveylin.ui.attribution.openUrl

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val versionName = BuildConfig.VERSION_NAME.ifBlank { "?" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.version_format, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.author_name),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.about_oss_statement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // The bundled dependency licenses, generated from the same SBOM
                // the license gate validates at build time.
                TextButton(
                    onClick = { showLicenses = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.licenses_title),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/Framstag/libosmscout")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Browser not available
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.about_source_url),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.osm_licence_statement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = { openUrl(context, OSM_COPYRIGHT_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.osm_licence_link),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )

    if (showDiagnostics) {
        DiagnosticsDialog(onDismiss = { showDiagnostics = false })
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

/**
 * Shows the captured crash/session log with a share button.
 *
 * The log is loaded on a background dispatcher (spec: auto-diagnostics — Reading
 * diagnostics does not block the UI): reading the file inside composition blocked
 * the main thread, and the share text is built from the same loaded snapshot so
 * the click does not read the file either. [DiagnosticsLogView] below is the pure
 * render half, so the layout and the share intent are testable without a load.
 */
@Composable
private fun DiagnosticsDialog(onDismiss: () -> Unit) {
    // null until the first background load returns (loading state).
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var shareText by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        // Share text first: once [entries] is published the share text is there too,
        // so clicking Share can never race the load (and nothing is read twice on
        // one thread).
        shareText = DiagnosticsLog.exportTextAsync()
        entries = DiagnosticsLog.readEntriesAsync()
    }

    DiagnosticsLogView(
        entries = entries,
        shareText = shareText,
        onRefresh = { reloadKey++ },
        onDismiss = onDismiss
    )
}

/**
 * Pure render half of the diagnostics dialog: no file access, no coroutines — the
 * caller supplies the entries (`null` while the load is in flight) and the share
 * text. Internal so the `:app` tests can drive it with fixed data.
 */
@Composable
internal fun DiagnosticsLogView(
    entries: List<String>?,
    shareText: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.diagnostics),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when {
                    entries == null -> Unit // load in flight; nothing to show yet
                    entries.isEmpty() -> Text(
                        text = stringResource(R.string.no_log_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> entries.takeLast(MAX_DISPLAYED_ENTRIES).asReversed().forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.refresh))
            }
        },
        dismissButton = {
            Row {
                val shareLabel = stringResource(R.string.share_diagnostics)
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent.createChooser(
                                DiagnosticsLog.shareIntent(shareText),
                                shareLabel
                            )
                        )
                    } catch (_: Exception) {
                        // No share target available
                    }
                }) {
                    Text(stringResource(R.string.share))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}

private const val MAX_DISPLAYED_ENTRIES = 100
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
