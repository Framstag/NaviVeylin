package com.naviveylin.ui.attribution

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import kotlinx.coroutines.delay

/**
 * OpenStreetMap attribution notice per the OSMF Attribution Guidelines
 * (https://osmfoundation.org/wiki/Licence/Attribution_Guidelines).
 *
 * Shows "© OpenStreetMap contributors" in a corner of the map, tappable to
 * open https://www.openstreetmap.org/copyright. The notice auto-hides after
 * five seconds of no map interaction (safe harbour allows fading after five
 * seconds or on map interaction); any map interaction (increment
 * [interactionTick]) re-shows it and restarts the timer. The "(i)" button
 * stays visible at all times so the licence information remains reachable
 * even when the notice is collapsed.
 *
 * @param interactionTick increments on every map pan/zoom interaction; the
 *   notice is re-shown and the auto-hide timer restarts on each change.
 */
@Composable
fun OsmAttributionOverlay(
    interactionTick: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(true) }
    var showLicenceDialog by remember { mutableStateOf(false) }

    // Auto-hide after 5s of no interaction; any interaction restarts the timer.
    LaunchedEffect(interactionTick) {
        visible = true
        delay(AUTO_HIDE_MILLIS)
        visible = false
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable { openUrl(context, OSM_COPYRIGHT_URL) }
            ) {
                Text(
                    text = stringResource(R.string.osm_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        FilledTonalIconButton(
            onClick = { showLicenceDialog = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.osm_licence_info_button)
            )
        }
    }

    if (showLicenceDialog) {
        OsmLicenceDialog(onDismiss = { showLicenceDialog = false })
    }
}

/**
 * Licence dialog: attribution text, the ODbL statement, and a link to
 * https://www.openstreetmap.org/copyright. Shown from the "(i)" button so
 * licence information stays reachable when the map notice is collapsed.
 */
@Composable
private fun OsmLicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.osm_licence_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.osm_licence_statement),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { openUrl(context, OSM_COPYRIGHT_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.osm_licence_link),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.osm_licence_close))
            }
        }
    )
}

/**
 * Opens [url] in the device browser. Best-effort: head units (AAOS) may have
 * no browser, in which case the call is a no-op.
 */
internal fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        // Required when the context is not an Activity (e.g. application context).
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // No browser available
    }
}

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val AUTO_HIDE_MILLIS = 5_000L
