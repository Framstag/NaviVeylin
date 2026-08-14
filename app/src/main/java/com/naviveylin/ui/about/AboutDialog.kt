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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naviveylin.core.DiagnosticsLog

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }

    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "NaviVeylin",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Version $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tim Teulings",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Copyright 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "An Android navigation app built on libosmscout. " +
                            "Provides offline map rendering and routing using " +
                            "OpenStreetMap data.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This application uses open source software. " +
                            "Source code and license information are available at:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                        text = "github.com/Framstag/libosmscout",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Diagnostics",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showDiagnostics) {
        DiagnosticsDialog(onDismiss = { showDiagnostics = false })
    }
}

/**
 * Shows the captured crash/session log with a share button.
 */
@Composable
private fun DiagnosticsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(DiagnosticsLog.readEntries()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Diagnostics",
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
                if (entries.isEmpty()) {
                    Text(
                        text = "No log entries yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.takeLast(MAX_DISPLAYED_ENTRIES).asReversed().forEach { line ->
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
            TextButton(onClick = {
                entries = DiagnosticsLog.readEntries()
            }) {
                Text("Refresh")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    try {
                        context.startActivity(
                            Intent.createChooser(DiagnosticsLog.shareIntent(), "Share diagnostics log")
                        )
                    } catch (_: Exception) {
                        // No share target available
                    }
                }) {
                    Text("Share")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

private const val MAX_DISPLAYED_ENTRIES = 100
