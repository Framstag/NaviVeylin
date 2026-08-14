package com.naviveylin.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.ui.navigation.NavSymbol
import com.naviveylin.ui.navigation.NavigationArrow
import com.naviveylin.ui.navigation.formatDistance

@Composable
fun RouteSummaryDialog(
    routeEntry: RouteEntry,
    steps: List<RouteStepDisplay>,
    activeStepIndex: Int? = null,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit = {},
    isNavigating: Boolean = false,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // Slide-up panel at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Route Summary", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Stats
                val durationSec = routeEntry.duration
                val hours = (durationSec / 3600.0).toInt()
                val minutes = ((durationSec % 3600.0) / 60.0).toInt()
                val durationText = if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        formatDistance(routeEntry.distance),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        durationText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                // Steps header
                Text(
                    "Steps",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(4.dp))

                // Step list
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    steps.forEachIndexed { index, step ->
                        val isActive = activeStepIndex == index
                        val bg = if (isActive) {
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        } else Modifier

                        Column(
                            modifier = bg.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                NavigationArrow(
                                    symbol = NavSymbol.TurnArrow(step.turnType),
                                    size = 28.dp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Column(
                                    modifier = Modifier.width(80.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    if (step.distanceText.isNotEmpty()) {
                                        Text(
                                            step.distanceText,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (step.timeText.isNotEmpty()) {
                                        Text(
                                            step.timeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    step.instruction,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Start / Stop Navigation
                if (isNavigating) {
                    Button(
                        onClick = onStopNavigation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("Stop Navigation")
                    }
                } else {
                    Button(
                        onClick = onStartNavigation,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Text("Start Navigation")
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
