package com.naviveylin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naviveylin.ui.about.AboutDialog

/**
 * Main hub screen. Shows welcome message when no map is displayed.
 * Navigate to Map Manager to download/select a map, then to Map Canvas.
 */
@Composable
fun MainScreen(
    onNavigateToMapManager: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Welcome content (centered)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NaviVeylin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Open-source offline navigation",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onNavigateToMapManager
            ) {
                Text("Get Maps")
            }
        }

        // Top-right overflow menu (below status bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp)
        ) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu"
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Download Maps") },
                    onClick = {
                        menuExpanded = false
                        onNavigateToMapManager()
                    }
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    onClick = {
                        menuExpanded = false
                        showAboutDialog = true
                    }
                )
            }
        }

        // About dialog
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }
}
