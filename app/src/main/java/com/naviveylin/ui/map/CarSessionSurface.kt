package com.naviveylin.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.naviveylin.R
import com.naviveylin.core.NavigationState
import com.naviveylin.core.TurnInstructionLocalizer
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import com.naviveylin.core.stringResolver
import com.naviveylin.ui.navigation.NavigationStatsRow

/**
 * What the phone shows while a car session owns the map (spec: `map-canvas-screen` — The
 * car-session surface is informative and offers the map back).
 *
 * The map canvas is disposed (not merely hidden) while this is composed, so the phone's
 * frame buffers, GL textures and bitmap tile cache are given up (design D1/D3). This surface
 * is therefore the phone's only content during the drive: it states where the guidance is, it
 * repeats the guidance summary from the SHARED navigation state, and it offers the one action
 * the spec asks for — bringing the map back for the rest of the session.
 *
 * Labels are not re-invented here. The maneuver line uses
 * [TurnInstructionLocalizer.shortDescription], the same localized description the car's
 * routing cue (`NavigationTemplateMapper`) and the phone's own next-turn card use, and the
 * distances go through the shared `com.naviveylin.core` formatters, which the car's
 * `distanceForDisplay` also delegates to — so phone and car describe the same state with the
 * same wording (spec: `cross-variant-ui-parity`).
 */
@Composable
fun CarSessionSurface(
    navigationState: NavigationState,
    onShowMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolver = remember(context) { context.stringResolver() }
    val instruction = navigationState.nextInstruction
    val destination = navigationState.destinationName?.takeIf { it.isNotBlank() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("carSessionSurface"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            // The car-session pill's statement, as this surface's headline: the pill is the
            // compact form of the same claim (folded in, never shown beside it).
            Text(
                text = stringResource(R.string.car_session_active_indicator),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("carSessionStatement")
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.car_session_surface_paused),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Guidance summary — only while the shared engine is actually navigating; a free
            // drive has no maneuver to summarize and the statement above is the whole surface.
            if (navigationState.isNavigating) {
                Spacer(Modifier.height(24.dp))
                if (destination != null) {
                    Text(
                        text = stringResource(R.string.destination),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = destination,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("carSessionDestination")
                    )
                }
                if (instruction != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            if (distanceUsesKilometers(instruction.distanceTo)) {
                                R.string.distance_unit_km
                            } else {
                                R.string.distance_unit_m
                            },
                            formatDistanceNumber(instruction.distanceTo)
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("carSessionNextDistance")
                    )
                    Text(
                        text = TurnInstructionLocalizer.shortDescription(resolver, instruction)
                            .takeIf { it.isNotBlank() }
                            ?: instruction.description,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("carSessionInstruction")
                    )
                }
                Spacer(Modifier.height(20.dp))
                // The phone's own stats row (arrival time, remaining time, remaining
                // distance), fed by the shared engine state. No stop action here: the
                // surface's single action is the map.
                NavigationStatsRow(
                    remainingDistance = navigationState.remainingDistance,
                    etaMillis = navigationState.etaMillis,
                    onStopNavigation = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("carSessionStats")
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onShowMap,
                modifier = Modifier.testTag("carSessionShowMap")
            ) {
                Text(stringResource(R.string.car_session_surface_show_map))
            }
        }
    }
}
