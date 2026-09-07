package com.naviveylin.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * On-map speed widget (spec: map-speed-widget): a current-speed badge with
 * the max speed as a round sign below it, mirroring the Android Auto
 * [com.naviveylin.auto.SurfaceIndicators] visualisation. The badge turns red
 * when the current speed exceeds the max speed by 5 km/h or more. Hidden
 * entirely when no current-speed data is available.
 *
 * @param reserveLimitSpace when true, the sign slot below the badge is always
 *   reserved (invisible when no limit is known) so the badge — and anything
 *   above it — stays in place when the limit sign appears or disappears.
 *   Needed for bottom-anchored placements; top-anchored placements are
 *   already stable and can leave it false.
 * @param reserveSlotWhenHidden when true and no current-speed data is
 *   available, the widget renders an invisible structure with the same
 *   footprint as the visible widget (badge + sign slot) instead of nothing,
 *   so anything above it (e.g. the compass) does not shift when the widget
 *   appears or disappears. Used for bottom-anchored placements.
 */
@Composable
fun SpeedWidget(
    currentSpeedKmH: Double,
    maxSpeedKmH: Double,
    modifier: Modifier = Modifier,
    reserveLimitSpace: Boolean = false,
    reserveSlotWhenHidden: Boolean = false
) {
    val hasSpeed = !currentSpeedKmH.isNaN() && currentSpeedKmH >= 0
    // No current speed (unknown or negative) → nothing to draw (spec:
    // "Widget hidden without speed data"), unless the slot must stay reserved.
    if (!hasSpeed && !reserveSlotWhenHidden) return

    val showLimit = hasSpeed && !maxSpeedKmH.isNaN() && maxSpeedKmH > 0
    val badgeColor = speedBadgeTextColor(isSpeedOverLimit(currentSpeedKmH, maxSpeedKmH))

    Column(
        modifier = modifier.then(
            if (hasSpeed) Modifier.testTag("speedWidget")
            else Modifier.testTag("speedWidgetSlot")
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (hasSpeed) speedBadgeContainerColor() else Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .then(if (hasSpeed) Modifier.testTag("speedBadge") else Modifier),
            contentAlignment = Alignment.Center
        ) {
            // Invisible widest-value text reserves the badge width so the badge
            // does not resize when the speed value changes (e.g. 48 → 120 km/h)
            // or the source switches (follow mode ↔ navigation).
            Text(
                text = MAX_SPEED_TEXT,
                color = Color.Transparent,
                style = speedBadgeTextStyle(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("speedBadgeMaxText")
            )
            if (hasSpeed) {
                Text(
                    text = "${currentSpeedKmH.roundToInt()} km/h",
                    color = badgeColor,
                    style = speedBadgeTextStyle(),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showLimit) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(64.dp)
                    .background(Color.White, CircleShape)
                    .border(6.dp, Color(0xFFE53935), CircleShape)
                    .testTag("speedLimitSign"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${maxSpeedKmH.roundToInt()}",
                    color = Color.Black,
                    style = speedLimitDigitsStyle(),
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (reserveLimitSpace || !hasSpeed) {
            // Invisible placeholder with the sign's footprint: keeps the badge
            // (and anything above it) at a fixed position when the limit sign
            // is hidden in bottom-anchored layouts.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(64.dp)
                    .testTag("speedLimitPlaceholder")
            )
        }
    }
}

/**
 * Badge text color: dark (`onSurface`) on the light card in the normal case;
 * the overspeed warning color when exceeding the limit by 5+ km/h (spec:
 * map-speed-widget — Overspeed warning color). Dark text on the light card
 * background; never white-on-light. Exposed for tests.
 */
@Composable
internal fun speedBadgeTextColor(overLimit: Boolean): Color =
    if (overLimit) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

/**
 * Badge container color — the standard overlay card container shared with the
 * turn card and routing status (spec: map-speed-widget — Speed badge uses the
 * standard overlay card container), never a fixed dark color. Exposed for
 * tests.
 */
@Composable
internal fun speedBadgeContainerColor(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)

/**
 * Badge current-speed text style — driver-seat readable size (spec:
 * map-speed-widget — minimum readable size for speed text). Exposed for
 * tests; the widget renders this style with bold weight.
 */
@Composable
internal fun speedBadgeTextStyle(): TextStyle = MaterialTheme.typography.headlineSmall

/**
 * Max-speed sign digit style — driver-seat readable size (spec:
 * map-speed-widget — minimum readable size for the max-speed sign). Exposed
 * for tests; the sign renders this style with bold weight.
 */
@Composable
internal fun speedLimitDigitsStyle(): TextStyle = MaterialTheme.typography.headlineMedium

/**
 * Overspeed rule (spec: map-speed-widget): the badge turns red when the
 * current speed exceeds the max speed by 5 km/h or more. Unknown max speed
 * (NaN or <= 0) never triggers red.
 */
internal fun isSpeedOverLimit(currentSpeedKmH: Double, maxSpeedKmH: Double): Boolean =
    !maxSpeedKmH.isNaN() && maxSpeedKmH > 0 && currentSpeedKmH > maxSpeedKmH + 5

/** Speed values to show in the widget; null when the widget must be hidden. */
internal data class SpeedWidgetInput(
    val currentSpeedKmH: Double,
    val maxSpeedKmH: Double
)

/**
 * Widest rendered speed value (3 digits max): reserves the badge width so the
 * badge does not resize when the value changes.
 */
private const val MAX_SPEED_TEXT = "999 km/h"

/**
 * Widget visibility + source selection (spec: map-speed-widget): shown when
 * (follow mode active OR navigating) AND current-speed data is available.
 * Navigation engine values win while navigating; GPS-derived values are used
 * in follow mode.
 */
internal fun speedWidgetInput(
    isNavigating: Boolean,
    navCurrentSpeedKmH: Double,
    navMaxSpeedKmH: Double,
    followCurrentSpeedKmH: Double,
    followMaxSpeedKmH: Double,
    followMode: Boolean
): SpeedWidgetInput? {
    val current = if (isNavigating) navCurrentSpeedKmH else followCurrentSpeedKmH
    val max = if (isNavigating) navMaxSpeedKmH else followMaxSpeedKmH
    val visible = (followMode || isNavigating) && !current.isNaN() && current >= 0
    return if (visible) SpeedWidgetInput(current, max) else null
}
