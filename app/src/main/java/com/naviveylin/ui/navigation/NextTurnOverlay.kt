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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framstag.libosmscout.client.LaneTurn
import com.framstag.libosmscout.client.RouteStep
import com.naviveylin.R
import com.naviveylin.core.Manoeuvre
import com.naviveylin.core.ManoeuvreLabels
import com.naviveylin.core.StringResolver
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.manoeuvreFromStep
import com.naviveylin.core.nextNextGapMeters
import com.naviveylin.core.remainingMeters
import com.naviveylin.core.stringResolver
import com.naviveylin.core.turnTypeFor

@Composable
fun NextTurnOverlay(
    step: RouteStep?,
    laneOneway: Boolean = false,
    laneCount: Int = 0,
    laneSuggested: Boolean = false,
    laneSuggestedFrom: Int = 0,
    laneSuggestedTo: Int = 0,
    laneTurns: List<LaneTurn> = emptyList(),
    laneHintsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (step == null) return

    val context = LocalContext.current
    val resolver = remember(context) { context.stringResolver() }
    val hasLanes = laneHintsEnabled && laneCount > 0 && laneTurns.isNotEmpty()
    val manoeuvre = remember(step) { manoeuvreFromStep(step) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
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
                    symbol = navSymbolFor(turnTypeFor(manoeuvre), manoeuvre.roundaboutExit ?: 0),
                    size = 64.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(end = 12.dp, top = 4.dp)
                        .testTag("nextTurnArrow")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (distanceUsesKilometers(step.remainingMeters)) R.string.distance_unit_km else R.string.distance_unit_m,
                            formatDistanceNumber(step.remainingMeters)
                        ),
                        style = nextTurnDistanceStyle(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val lines = instructionLines(resolver, manoeuvre)
                    Text(
                        text = lines.generic,
                        style = nextTurnDescriptionStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (lines.destination != null) {
                        Text(
                            text = lines.destination,
                            style = nextTurnDescriptionStyle(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
            val nextNext = step.nextNext
            if (nextNext != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val nextNextManoeuvre = remember(nextNext) { manoeuvreFromStep(nextNext) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavigationArrow(
                        symbol = navSymbolFor(
                            turnTypeFor(nextNextManoeuvre),
                            nextNextManoeuvre.roundaboutExit ?: 0
                        ),
                        size = 36.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("nextTurnNextNextArrow")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (distanceUsesKilometers(step.nextNextGapMeters)) R.string.distance_unit_km else R.string.distance_unit_m,
                                formatDistanceNumber(step.nextNextGapMeters)
                            ),
                            style = nextTurnNextNextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val nextNextLines = instructionLines(resolver, nextNextManoeuvre)
                        Text(
                            text = nextNextLines.generic,
                            style = nextTurnNextNextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (nextNextLines.destination != null) {
                            Text(
                                text = nextNextLines.destination,
                                style = nextTurnNextNextStyle(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

/**
 * Next-turn distance style — driver-seat readable size (spec:
 * next-turn-overlay — Driver-seat readable font sizes).
 */
@Composable
internal fun nextTurnDistanceStyle(): TextStyle = MaterialTheme.typography.headlineLarge

/**
 * Next-turn description/destination style — driver-seat readable size.
 */
@Composable
internal fun nextTurnDescriptionStyle(): TextStyle = MaterialTheme.typography.titleLarge

/**
 * Next-next hint style — driver-seat readable size, still smaller than the
 * primary instruction.
 */
@Composable
internal fun nextTurnNextNextStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp)

/**
 * The two lines a guidance row shows: the manoeuvre itself ("Turn left") and the street or motorway
 * it leads onto. Both come from the manoeuvre's own values — there is no description to split because
 * the native layer publishes none (spec: `turn-instruction-localization`).
 */
internal data class InstructionLines(
    val generic: String,
    val destination: String?
)

internal fun instructionLines(resolver: StringResolver, manoeuvre: Manoeuvre): InstructionLines =
    InstructionLines(
        generic = ManoeuvreLabels.short(resolver, manoeuvre),
        destination = (manoeuvre.displayName ?: manoeuvre.motorwayTo)?.takeIf { it.isNotBlank() }
    )
