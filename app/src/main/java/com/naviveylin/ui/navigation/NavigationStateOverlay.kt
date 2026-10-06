package com.naviveylin.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.naviveylin.R
import com.naviveylin.core.distanceUsesKilometers
import com.naviveylin.core.formatDistanceNumber
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.framstag.libosmscout.client.CurrentRoadInfo

@Composable
fun NavigationStateOverlay(
    remainingDistance: Double,
    etaMillis: Long,
    currentRoadInfo: CurrentRoadInfo? = null,
    distanceProgressPercent: Int? = null,
    timeProgressPercent: Int? = null,
    isRerouting: Boolean = false,
    isOffRoute: Boolean = false,
    onStopNavigation: () -> Unit = {},
    /**
     * Opens the expanded route description. It covers the card's content **except** the
     * stop control, whose own hit area never overlaps this one (spec:
     * navigation-status-details — Routing status card is clickable).
     */
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val cardContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

    val content = @Composable {
        Card(
            modifier = Modifier.fillMaxWidth(),
            // Square bottom corners: the card covers the bottom of the window.
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = cardContainerColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
            ) {
                // The details tap lives on the card's own content, never on the whole
                // card: it stops short of the stop control, so the two hit areas cannot
                // overlap (spec: navigation-status-details — Routing status card is
                // clickable). This block covers the road name and the progress lines;
                // the stats strip below carries the same tap on its leading part.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick)
                        .testTag("navStatusDetailsRegion")
                ) {
                    // Current road name row (above stats)
                    Text(
                        text = currentRoadText(currentRoadInfo, stringResource(R.string.road_offroad)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (currentRoadInfo != null && currentRoadInfo.hasInfo())
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )

                    // Route progress lines — always visible during navigation
                    // (spec: navigation-status-details — "Route progress lines in
                    // routing status card"). Small, no labels, no percent values.
                    if (distanceProgressPercent != null && timeProgressPercent != null) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            ProgressLine(
                                icon = Icons.Default.Place,
                                contentDescription = stringResource(R.string.distance),
                                percent = distanceProgressPercent,
                                modifier = Modifier.testTag("distanceProgressLine")
                            )
                            Spacer(Modifier.height(3.dp))
                            ProgressLine(
                                icon = Icons.Default.Schedule,
                                contentDescription = stringResource(R.string.remaining_time),
                                percent = timeProgressPercent,
                                modifier = Modifier.testTag("timeProgressLine")
                            )
                        }
                    }
                }

                // Stats row — its leading strip carries the same details tap (one region
                // for the three stats, the stop control stays outside it).
                NavigationStatsRow(
                    remainingDistance = remainingDistance,
                    etaMillis = etaMillis,
                    onStopNavigation = onStopNavigation,
                    leadingModifier = Modifier
                        .clickable(onClick = onClick)
                        .testTag("navStatusDetailsStatsRegion")
                )
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
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
            )
        }
    }
}

/** ETA / remaining time / distance stats row, shared with the expanded details view. */
@Composable
internal fun NavigationStatsRow(
    remainingDistance: Double,
    etaMillis: Long,
    /** Stop action; null renders no stop button (the car-session surface has none). */
    onStopNavigation: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * Applied to the leading stats strip only (ETA / remaining time / distance) — never to
     * the stop control. A caller that makes the strip tappable therefore keeps its hit area
     * disjoint from the control's (spec: navigation-status-details — Routing status card is
     * clickable).
     */
    leadingModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(leadingModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ETA
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.eta),
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
                    contentDescription = stringResource(R.string.remaining_time),
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
                    contentDescription = stringResource(R.string.distance),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        if (distanceUsesKilometers(remainingDistance)) R.string.distance_unit_km else R.string.distance_unit_m,
                        formatDistanceNumber(remainingDistance)
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Stop button (compact icon-only, in the status row) — only when the surface
        // offers one. Its touch box is a full 48 dp and it is the only child of this row
        // outside the leading strip, so a caller's strip tap cannot cover it.
        if (onStopNavigation != null) {
            IconButton(
                onClick = onStopNavigation,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("stopNavigation")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.stop_navigation),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Current road name text ("ref name", or the caller's off-road label), shared with the
 *  expanded details view. The off-road wording is a resource owned by the surface that
 *  renders it (spec: i18n-l10n — All user-facing text is translatable). */
internal fun currentRoadText(currentRoadInfo: CurrentRoadInfo?, offroadLabel: String): String {
    return when {
        currentRoadInfo != null && currentRoadInfo.hasInfo() -> {
            listOfNotNull(
                currentRoadInfo.ref.takeIf { it.isNotEmpty() },
                currentRoadInfo.name.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
        }
        else -> offroadLabel
    }
}

internal fun formatRemainingTime(etaMillis: Long): String {
    if (etaMillis <= 0) return "--"
    val remaining = etaMillis - System.currentTimeMillis()
    if (remaining <= 0) return "0 min"
    val totalMinutes = remaining / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes} min"
}

/**
 * One small progress line: a 16 dp icon for differentiation, then a thin track
 * with a filled portion up to the current percent. No labels, no percent
 * values (spec: navigation-status-details — "Route progress lines in routing
 * status card").
 */
@Composable
private fun ProgressLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    percent: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(1.5.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(3.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(1.5.dp)
                    )
            )
        }
    }
}
