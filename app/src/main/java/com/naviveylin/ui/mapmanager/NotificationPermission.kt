package com.naviveylin.ui.mapmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that runs [action] immediately when notifications are
 * already granted (or on API < 33, where POST_NOTIFICATIONS does not exist),
 * and otherwise requests POST_NOTIFICATIONS first and runs the action only if
 * the user grants it. The pending action survives the permission-dialog round
 * trip, so the download starts right after granting.
 *
 * Used by the map-download entry points: the foreground download service
 * (MapDownloadService) posts a progress notification, which on Android 13+
 * requires this runtime permission.
 */
@Composable
fun rememberNotificationPermissionLauncher(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }
    return { action ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
