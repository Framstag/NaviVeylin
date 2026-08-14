package com.naviveylin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.framstag.libosmscout.client.CurrentRoadInfo

@Composable
fun NavigationStateOverlay(
    remainingDistance: Double,
    etaMillis: Long,
    currentSpeedKmH: Double,
    maxSpeedKmH: Double,
    currentRoadInfo: CurrentRoadInfo? = null,
    isRerouting: Boolean = false,
    isOffRoute: Boolean = false,
    onStopNavigation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

    val content = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardContainerColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
            ) {
                // Current road name row (above stats)
                val roadText = when {
                    currentRoadInfo != null && currentRoadInfo.hasInfo() -> {
                        listOfNotNull(
                            currentRoadInfo.ref.takeIf { it.isNotEmpty() },
                            currentRoadInfo.name.takeIf { it.isNotEmpty() }
                        ).joinToString(" ")
                    }
                    else -> "Offroad"
                }
                Text(
                    text = roadText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (currentRoadInfo != null && currentRoadInfo.hasInfo())
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                )

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ETA
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "ETA",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (etaMillis > 0) {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(etaMillis))
                            } else "--:--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remaining time
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Remaining time",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formatRemainingTime(etaMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Remaining distance
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = "Distance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = formatDistance(remainingDistance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                        // Current speed + max speed (always reserve space for max)
                        // Show speed in red when exceeding max allowed speed by 5+ km/h
                        val speedColor = if (!currentSpeedKmH.isNaN() && currentSpeedKmH >= 0 &&
                            !maxSpeedKmH.isNaN() && maxSpeedKmH > 0 &&
                            currentSpeedKmH > maxSpeedKmH + 5
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (currentSpeedKmH.isNaN() || currentSpeedKmH < 0) "--" else "${currentSpeedKmH.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = speedColor
                            )
                            Text(
                                text = if (!maxSpeedKmH.isNaN() && maxSpeedKmH > 0) "max ${maxSpeedKmH.toInt()}" else "max --",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.height(16.dp) // fixed height prevents resizing
                            )
                        }

                    // Stop button (compact icon-only, in the status row)
                    IconButton(
                        onClick = onStopNavigation,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop navigation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        content()
        if (isOffRoute) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
            )
        }
    }
}

private fun formatRemainingTime(etaMillis: Long): String {
    if (etaMillis <= 0) return "--"
    val remaining = etaMillis - System.currentTimeMillis()
    if (remaining <= 0) return "0 min"
    val totalMinutes = remaining / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
}