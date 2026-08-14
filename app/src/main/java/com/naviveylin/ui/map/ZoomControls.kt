package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

@Composable
fun ZoomControls(
    canZoomIn: Boolean,
    canZoomOut: Boolean,
    currentMag: Int = 0,
    isLandscape: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.shadow(3.dp, RoundedCornerShape(24.dp))
    ) {
        if (isLandscape) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onZoomIn,
                    enabled = canZoomIn,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in"
                    )
                }

                Text(
                    text = "$currentMag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .width(36.dp)
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                FilledIconButton(
                    onClick = onZoomOut,
                    enabled = canZoomOut,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom out"
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledIconButton(
                    onClick = onZoomIn,
                    enabled = canZoomIn,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom in"
                    )
                }

                Text(
                    text = "$currentMag",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .width(36.dp)
                        .padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                FilledIconButton(
                    onClick = onZoomOut,
                    enabled = canZoomOut,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom out"
                    )
                }
            }
        }
    }
}
