package com.naviveylin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteInstruction

@Composable
fun NextTurnOverlay(
    instruction: RouteInstruction?,
    laneOneway: Boolean = false,
    laneCount: Int = 0,
    laneSuggested: Boolean = false,
    laneSuggestedFrom: Int = 0,
    laneSuggestedTo: Int = 0,
    laneTurns: List<LaneTurn> = emptyList(),
    laneHintsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (instruction == null) return

    val hasLanes = laneHintsEnabled && laneCount > 0 && laneTurns.isNotEmpty()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 0.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Row 1: Turn icon + distance + description (next turn)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                NavigationArrow(
                    symbol = NavSymbol.TurnArrow(instruction.turnType),
                    size = 48.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 12.dp, top = 4.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDistance(instruction.distanceTo),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = instruction.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Row 2: Lane guidance (optional — no gap when absent)
            if (hasLanes) {
                Spacer(modifier = Modifier.height(4.dp))
                LaneHintsRow(
                    oneway = laneOneway,
                    count = laneCount,
                    suggested = laneSuggested,
                    suggestedFrom = laneSuggestedFrom,
                    suggestedTo = laneSuggestedTo,
                    turns = laneTurns
                )
            }

            // Row 3: Next-next turn hint (optional)
            if (instruction.hasNextNext()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationArrow(
                        symbol = NavSymbol.TurnArrow(instruction.nextNextTurnType),
                        size = 28.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatDistance(instruction.nextNextDistanceTo),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = instruction.nextNextDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaneHintsRow(
    oneway: Boolean,
    count: Int,
    suggested: Boolean,
    suggestedFrom: Int,
    suggestedTo: Int,
    turns: List<LaneTurn>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in turns.indices) {
            if (i > 0 && !oneway) {
                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.outlineVariant,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            }
            val isSuggested = suggested && i >= suggestedFrom && i <= suggestedTo
            val bgColor = if (isSuggested) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                          else androidx.compose.ui.graphics.Color.Transparent
            val arrowColor = if (isSuggested) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
            Box(
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(6.dp))
                    .padding(2.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                NavigationArrow(
                    symbol = NavSymbol.LaneArrow(turns[i]),
                    size = 36.dp,
                    color = arrowColor
                )
            }
        }
    }
}
