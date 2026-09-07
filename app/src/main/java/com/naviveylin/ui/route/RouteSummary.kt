package com.naviveylin.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.R
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.formatDurationText
import com.naviveylin.ui.navigation.NavSymbol
import com.naviveylin.ui.navigation.NavigationArrow

/**
 * Reusable route summary: total distance, estimated duration, and the
 * turn-by-turn step list. Embedded in the route panel after calculation and
 * reused by [RouteSummaryDialog] (spec: routing-summary).
 */
@Composable
fun RouteSummary(
    routeEntry: RouteEntry,
    steps: List<RouteStepDisplay>,
    activeStepIndex: Int? = null,
    scrollable: Boolean = true
) {
    // Stats
    val durationText = formatDurationText(routeEntry.duration)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            stringResource(
                if (distanceUsesKilometers(routeEntry.distance)) R.string.distance_unit_km else R.string.distance_unit_m,
                formatDistanceNumber(routeEntry.distance)
            ),
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
        stringResource(R.string.steps),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(4.dp))

    // Step list
    val stepListModifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState())
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    }
    Column(modifier = stepListModifier) {
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag(if (isActive) "activeStep" else "step")
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
