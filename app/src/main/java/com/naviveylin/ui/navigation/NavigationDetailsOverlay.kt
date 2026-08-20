package com.naviveylin.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.CurrentRoadInfo
import com.framstag.libosmscout.client.RouteInstruction

/**
 * Full-screen expansion of the routing status card during active navigation.
 * Keeps the status content (current road name + stats) and shows the route
 * description list, styled like the route details view, with the current
 * navigation step highlighted and shown at the top of the list.
 */
@Composable
fun NavigationDetailsOverlay(
    instructions: List<RouteInstruction>,
    currentStepIndex: Int,
    currentRoadInfo: CurrentRoadInfo? = null,
    remainingDistance: Double,
    etaMillis: Long,
    currentSpeedKmH: Double,
    maxSpeedKmH: Double,
    onStopNavigation: () -> Unit,
    onDismiss: () -> Unit
) {
    // System back dismisses the details view.
    BackHandler {
        onDismiss()
    }

    // Open with the current step as the first visible item; re-scroll as the
    // user progresses so the current step stays at the top.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentStepIndex)
    LaunchedEffect(currentStepIndex) {
        if (currentStepIndex in instructions.indices) {
            listState.animateScrollToItem(currentStepIndex)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Navigation", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Status content: current road name + stats
            Text(
                text = currentRoadText(currentRoadInfo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = if (currentRoadInfo != null && currentRoadInfo.hasInfo())
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationStatsRow(
                remainingDistance = remainingDistance,
                etaMillis = etaMillis,
                currentSpeedKmH = currentSpeedKmH,
                maxSpeedKmH = maxSpeedKmH,
                onStopNavigation = onStopNavigation
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(4.dp))

            // Route description list
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(instructions) { index, instruction ->
                    val isActive = index == currentStepIndex
                    val bg = if (isActive) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                    } else Modifier

                    Column(
                        modifier = bg
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            NavigationArrow(
                                symbol = NavSymbol.TurnArrow(instruction.turnType),
                                size = 28.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Column(
                                modifier = Modifier.width(80.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    formatDistance(instruction.distanceTo),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                instruction.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(if (isActive) "activeStep" else "step")
                            )
                        }
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
