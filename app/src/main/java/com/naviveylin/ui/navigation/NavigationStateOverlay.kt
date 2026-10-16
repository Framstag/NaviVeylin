package com.naviveylin.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routingStatusCard"),
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
                // clickable). This region covers the road name — the progress lines that
                // used to sit under it are gone; the stats strip below carries the same tap
                // on its leading part.
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
                }

                // Stats row — its leading strip carries the same details tap (one region
                // for the three stats, the stop control stays outside it).
                NavigationStatsRow(
                    remainingDistance = remainingDistance,
                    etaMillis = etaMillis,
                    distanceProgressPercent = distanceProgressPercent,
                    timeProgressPercent = timeProgressPercent,
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
    /**
     * Percent of the route's distance travelled, or null when that value's progress is unknown —
     * the glyph then keeps its slot and is drawn without a ring (spec: navigation-status-details
     * — A glyph without progress keeps its place in the row).
     */
    distanceProgressPercent: Int? = null,
    /** Percent of the estimated travel time elapsed, or null when it is unknown. */
    timeProgressPercent: Int? = null,
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
            // ETA — deliberately un-ringed: the arrival time is the same dimension as the
            // remaining time, so a second ring would show one percent twice, and a plain glyph
            // beside the ringed clock would read as "no data" if it were also a clock
            // (spec: navigation-status-details — The two rings are told apart from the plain
            // glyph).
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavStatIcon(
                    glyph = Icons.Default.Flag,
                    contentDescription = stringResource(R.string.eta),
                    slotTag = "etaGlyphSlot"
                )
                Text(
                    text = if (etaMillis > 0) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(etaMillis))
                    } else "--:--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Remaining time — ringed with the elapsed share of the estimated travel time.
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavStatIcon(
                    glyph = Icons.Default.Schedule,
                    contentDescription = stringResource(R.string.remaining_time),
                    percent = timeProgressPercent,
                    slotTag = "timeGlyphSlot",
                    ringTag = "timeProgressRing"
                )
                Text(
                    text = formatRemainingTime(etaMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Remaining distance — ringed with the travelled share of the route.
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavStatIcon(
                    glyph = Icons.Default.Place,
                    contentDescription = stringResource(R.string.distance),
                    percent = distanceProgressPercent,
                    slotTag = "distanceGlyphSlot",
                    ringTag = "distanceProgressRing"
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
 * The value arc's sweep in degrees for [percent]: the completed fraction of that value's own
 * dimension, from 0 (nothing done) to 360 (arrived). The percent is clamped to the valid
 * 0-100 range before it is scaled, so a caller cannot draw past the ring
 * (spec: navigation-status-details — Progress values clamped to valid range).
 */
internal fun ringSweepDegrees(percent: Int): Float = percent.coerceIn(0, 100) * 3.6f

/** The fixed side of every value glyph's slot in the routing status row. */
private val NavStatSlot = 28.dp

/** The glyph inside that slot: unchanged in size, so a ring around it costs no legibility. */
private val NavStatGlyph = 16.dp

/** The ring's stroke width — what the progress line it replaces was tall. */
private val NavStatRingStroke = 3.dp

/**
 * One value glyph of the routing status row, drawn in a fixed [slot] so a value whose progress is
 * unknown keeps its place instead of shifting the row (spec: navigation-status-details — A glyph
 * without progress keeps its place in the row).
 *
 * A non-null [percent] rings the glyph with that value's own progress: a full track and a value
 * arc filled clockwise from twelve o'clock with the completed fraction of the dimension
 * (spec: navigation-status-details — Route progress rings on the routing status row). The ring
 * uses the same palette as the progress line it replaces (theme `primary` over `surfaceVariant`)
 * and a stroke expressed in dp and converted at draw time — never a raw device-pixel count
 * (guidelines/UI.md §8, "Drawn dimensions are density-independent"). It carries no progress
 * semantics: the glyph's own content description names the value, and the number under it is the
 * value itself.
 */
@Composable
private fun NavStatIcon(
    glyph: ImageVector,
    contentDescription: String,
    percent: Int? = null,
    slotTag: String? = null,
    ringTag: String? = null,
    slot: Dp = NavStatSlot,
    glyphSize: Dp = NavStatGlyph
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val valueColor = MaterialTheme.colorScheme.primary
    val glyphColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(slot)
            .then(if (slotTag != null) Modifier.testTag(slotTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (percent != null) {
            Canvas(
                modifier = Modifier
                    .size(slot)
                    .then(if (ringTag != null) Modifier.testTag(ringTag) else Modifier)
            ) {
                val stroke = NavStatRingStroke.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = valueColor,
                    startAngle = -90f,
                    sweepAngle = ringSweepDegrees(percent),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Icon(
            imageVector = glyph,
            contentDescription = contentDescription,
            tint = glyphColor,
            modifier = Modifier.size(glyphSize)
        )
    }
}
