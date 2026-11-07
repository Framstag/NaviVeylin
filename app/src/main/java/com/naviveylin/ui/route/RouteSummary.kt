package com.naviveylin.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.R
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.formatDurationText
import com.naviveylin.core.formatStepDurationText
import com.naviveylin.ui.navigation.NavigationArrow
import com.naviveylin.ui.navigation.navSymbolFor

/**
 * Reusable route summary: total distance, estimated duration, and the
 * turn-by-turn row list. Embedded in the route panel after calculation and
 * reused by the session overlay's summary (spec: routing-summary). The phone card passes
 * [showStats] = false because its header already carries the two numbers (keeping the card's
 * height for the list); the docked panel and the other surfaces use the default headline.
 *
 * The list is the route's **rows**: derived from the structured steps (spec: `route-analysis` — Rows
 * are derived from the route's steps), worded from the manoeuvre's own values in the device language
 * (spec: `turn-instruction-localization` — Route analysis rows are localized from the step model).
 *
 * A row's distance and duration are its **own run** — its own leg plus the legs of every step it
 * absorbed — formatted here by the app (`formatDistanceNumber`, `formatStepDurationText`), never a
 * native description's `[x km, y min]` text.
 *
 * The step list carries two independent markings (spec: `route-analysis` — the
 * analysed step is distinguishable from the navigation step): [activeStepIndex] is
 * the step navigation is currently on (filled row, bold text), [analysedStepIndex]
 * is the step the user selected to inspect (outlined row plus the `selected` state,
 * so the two never rely on colour alone). When [onStepSelected] is supplied the rows
 * become tappable and report their index.
 */
@Composable
fun RouteSummary(
    routeEntry: RouteEntry,
    steps: List<RouteStepDisplay>,
    activeStepIndex: Int? = null,
    scrollable: Boolean = true,
    showStats: Boolean = true,
    analysedStepIndex: Int? = null,
    onStepSelected: ((Int) -> Unit)? = null
) {
    // Stats
    val durationText = formatDurationText(routeEntry.duration)
    // The route's own length: the sum of the steps this component lists, so the statistic above the
    // list and the list itself cannot state two lengths (`routeLengthMeters`, spec: `osmscout-jni` —
    // One route length for a calculated route).
    val routeLength = routeLengthMeters(routeEntry)

    if (showStats) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                stringResource(
                    if (distanceUsesKilometers(routeLength)) R.string.distance_unit_km else R.string.distance_unit_m,
                    formatDistanceNumber(routeLength)
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
    }

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
        if (steps.isEmpty()) {
            // A route without structured steps has no rows (spec: `route-analysis` — A route without
            // per-step values still lists its steps): the list says so instead of inventing one.
            Text(
                stringResource(R.string.route_steps_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 8.dp)
                    .testTag("routeStepsEmpty")
            )
        }
        steps.forEachIndexed { index, step ->
            val isActive = activeStepIndex == index
            val isAnalysed = analysedStepIndex == index
            val shape = RoundedCornerShape(8.dp)
            val background = if (isActive) {
                Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
            } else Modifier
            // The analysed step's cue is the outline (a shape, not a colour) plus the
            // `selected` state, so it stays distinguishable from the navigation fill.
            val outline = if (isAnalysed) {
                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
            } else Modifier
            val click = if (onStepSelected != null) {
                Modifier.clickable { onStepSelected(index) }
            } else Modifier
            val selectionState = Modifier.semantics { selected = isAnalysed }

            Column(
                modifier = background
                    .then(outline)
                    .then(click)
                    .then(selectionState)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag("routeStep$index")
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    NavigationArrow(
                        // The glyph comes from the manoeuvre's kind and move, never from the row's
                        // text (spec: `turn-instruction-localization` — The row icon follows the
                        // manoeuvre, not the text). A roundabout leave therefore carries its exit
                        // ordinal, which the structured step publishes and the old description line
                        // never did.
                        symbol = navSymbolFor(step.turnType, step.roundaboutExit ?: 0),
                        size = 28.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier.width(80.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (step.distanceMeters > 0.0) {
                            Text(
                                stringResource(
                                    if (distanceUsesKilometers(step.distanceMeters)) R.string.distance_unit_km
                                    else R.string.distance_unit_m,
                                    formatDistanceNumber(step.distanceMeters)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        val stepTime = formatStepDurationText(step.durationSeconds)
                        if (stepTime.isNotEmpty()) {
                            Text(
                                stepTime,
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
