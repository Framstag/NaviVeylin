package com.naviveylin.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPS fix quality indicator colors — light variants.
 */
private val GpsRingNoFix = Color(0xFFFFCDD2)   // light red
private val GpsRingPoorFix = Color(0xFFFFF9C4)  // light yellow
private val GpsRingGoodFix = Color(0xFFC8E6C9)  // light green

/**
 * Animated compass button showing north direction, GPS fix quality ring,
 * and supporting short-press (re-center) and long-press (toggle orientation).
 *
 * @param isNorthUp True if orientation is "always north" (north-up), false for "follow direction".
 * @param mapAngleRadians Current map rotation in radians (0 = north up).
 * @param gpsFixQuality Current GPS fix quality for ring color.
 * @param onCenterClick Called on short press to re-center on location.
 * @param onToggleOrientation Called on long press to toggle north-up / follow-direction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompassButton(
    isNorthUp: Boolean,
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert radians to degrees for rotation, negate so compass points north
    val targetDegrees = (-Math.toDegrees(mapAngleRadians)).toFloat()
    val animatedDegrees by animateFloatAsState(
        targetValue = targetDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "compassRotation"
    )

    val ringColor = when (gpsFixQuality) {
        GpsFixQuality.NONE -> GpsRingNoFix
        GpsFixQuality.POOR -> GpsRingPoorFix
        GpsFixQuality.GOOD -> GpsRingGoodFix
    }

    val bgColor = if (isNorthUp) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .then(
                Modifier.combinedClickable(
                    onClick = onCenterClick,
                    onLongClick = onToggleOrientation
                )
            )
    ) {
        // Background circle matching other overlay buttons
        androidx.compose.foundation.Canvas(modifier = Modifier.size(48.dp)) {
            drawCircle(color = bgColor)

            // GPS fix quality ring (outer)
            drawCompassRing(ringColor)

            // Compass needle
            drawCompassNeedle(animatedDegrees, isNorthUp, textMeasurer)
        }
    }
}

/**
 * Draw the GPS fix quality ring — a thick circle just inside the button edge.
 */
private fun DrawScope.drawCompassRing(color: Color) {
    val ringRadius = size.minDimension / 2f - 4f
    val ringWidth = 3.dp.toPx()
    drawCircle(
        color = color,
        radius = ringRadius,
        style = Stroke(width = ringWidth)
    )
}

/**
 * Draw a simple compass needle: red north-pointing half, grey south half.
 * In "always north" mode, draws a prominent red "N" at the north tip.
 * In "follow direction" mode, draws a blue directional arrow indicator.
 * Rotated by [degrees] (0 = north up).
 */
private fun DrawScope.drawCompassNeedle(degrees: Float, isNorthUp: Boolean, textMeasurer: androidx.compose.ui.text.TextMeasurer) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val needleLength = size.minDimension / 2f - 8f
    val radians = Math.toRadians(degrees.toDouble())

    // North tip
    val northX = centerX + (needleLength * sin(radians)).toFloat()
    val northY = centerY - (needleLength * cos(radians)).toFloat()

    // South tip
    val southX = centerX - (needleLength * sin(radians)).toFloat()
    val southY = centerY + (needleLength * cos(radians)).toFloat()

    if (isNorthUp) {
        // "Always north" mode: red "N" at north tip, neutral grey south
        // North half
        drawLine(
            color = Color(0xFFE53935), // red
            start = androidx.compose.ui.geometry.Offset(centerX, centerY),
            end = androidx.compose.ui.geometry.Offset(northX, northY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // South half
        drawLine(
            color = Color(0xFF9E9E9E), // grey
            start = androidx.compose.ui.geometry.Offset(centerX, centerY),
            end = androidx.compose.ui.geometry.Offset(southX, southY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Red "N" at north tip
        val textResult = textMeasurer.measure(
            text = "N",
            style = TextStyle(
                color = Color(0xFFE53935),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
        val nOffsetX = northX - textResult.size.width / 2f
        val nOffsetY = northY - textResult.size.height / 2f
        drawText(
            textLayoutResult = textResult,
            topLeft = androidx.compose.ui.geometry.Offset(nOffsetX, nOffsetY)
        )
    } else {
        // "Follow direction" mode: blue directional arrow
        val arrowColor = Color(0xFF4A90D9)

        // Draw a small arrow triangle at the north tip
        val arrowSize = 6f
        val arrowTipX = northX
        val arrowTipY = northY
        val arrowLeftX = centerX + ((needleLength - arrowSize) * sin(radians - 0.4)).toFloat()
        val arrowLeftY = centerY - ((needleLength - arrowSize) * cos(radians - 0.4)).toFloat()
        val arrowRightX = centerX + ((needleLength - arrowSize) * sin(radians + 0.4)).toFloat()
        val arrowRightY = centerY - ((needleLength - arrowSize) * cos(radians + 0.4)).toFloat()

        // Arrow body (line from center to tip)
        drawLine(
            color = arrowColor,
            start = androidx.compose.ui.geometry.Offset(centerX, centerY),
            end = androidx.compose.ui.geometry.Offset(northX, northY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Arrow head (triangle)
        drawLine(
            color = arrowColor,
            start = androidx.compose.ui.geometry.Offset(arrowTipX, arrowTipY),
            end = androidx.compose.ui.geometry.Offset(arrowLeftX, arrowLeftY),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = arrowColor,
            start = androidx.compose.ui.geometry.Offset(arrowTipX, arrowTipY),
            end = androidx.compose.ui.geometry.Offset(arrowRightX, arrowRightY),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )

        // South half (short tail)
        drawLine(
            color = arrowColor.copy(alpha = 0.4f),
            start = androidx.compose.ui.geometry.Offset(centerX, centerY),
            end = androidx.compose.ui.geometry.Offset(southX, southY),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}
