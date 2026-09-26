package com.naviveylin.ui.map

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naviveylin.R
import com.naviveylin.core.R as CoreR

/** What the precise-location upgrade action must do when the user accepts it. */
internal enum class PreciseLocationAction {
    /** The platform can still ask — re-request the location permissions. */
    REQUEST_PERMISSION,

    /** The platform will not ask again — only the app's settings can grant it. */
    OPEN_SETTINGS
}

/**
 * The upgrade action for the navigation gate (spec: `location-permissions` — Phone
 * offers the upgrade action). As long as the system can still show the dialog the
 * app re-requests; once it will not ask again ("Don't ask again", or a second
 * denial) the app's system settings are the only route.
 */
internal fun preciseLocationAction(permanentlyDenied: Boolean): PreciseLocationAction =
    if (permanentlyDenied) {
        PreciseLocationAction.OPEN_SETTINGS
    } else {
        PreciseLocationAction.REQUEST_PERMISSION
    }

/**
 * The refusal notice for a route request without the precise grant (spec:
 * `location-permissions` — Starting navigation requires precise location). Wording
 * comes from the shared `:core` string, so the phone dialog and the car message can
 * not drift.
 */
@Composable
fun PreciseLocationRequiredDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_permission_needed)) },
        text = { Text(stringResource(CoreR.string.location_precise_required_navigation)) },
        confirmButton = {
            TextButton(onClick = onUpgrade) {
                Text(stringResource(CoreR.string.location_grant_precise_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
