package com.naviveylin.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPS fix quality indicator colors — light variants, used as the button fill.
 */
private val GpsFillNoFix = Color(0xFFFFCDD2)   // light red
private val GpsFillPoorFix = Color(0xFFFFF9C4) // light yellow
private val GpsFillGoodFix = Color(0xFFC8E6C9) // light green

/**
 * Animated compass button showing north direction, GPS fix quality as the
 * button fill color, and supporting short-press (re-center) and long-press
 * (toggle orientation).
 *
 * Sized like the other overlay buttons (48dp layout / 40dp visual, matching
 * `FilledTonalIconButton`) with the same shadow, per Material 3 usage
 * elsewhere on the map.
 *
 * @param isNorthUp True if orientation is "always north" (north-up), false for "follow direction".
 * @param mapAngleRadians Current map rotation in radians (0 = north up).
 * @param gpsFixQuality Current GPS fix quality for the fill color.
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

    val fillColor = when (gpsFixQuality) {
        GpsFixQuality.NONE -> GpsFillNoFix
        GpsFixQuality.POOR -> GpsFillPoorFix
        GpsFixQuality.GOOD -> GpsFillGoodFix
    }

    val borderColor = MaterialTheme.colorScheme.outline
    // Same symbol color as the other overlay buttons (FilledTonalIconButton icons)
    val needleColor = MaterialTheme.colorScheme.onSecondaryContainer
    val textMeasurer = rememberTextMeasurer()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 48dp layout matches FilledTonalIconButton (40dp visual + touch target),
            // so the compass aligns with the other overlay buttons in the column
            .size(48.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Compass" }
            .then(
                Modifier.combinedClickable(
                    onClick = onCenterClick,
                    onLongClick = onToggleOrientation
                )
            )
    ) {
        // 40dp visual fill matching other overlay buttons, colored by GPS fix quality
        Canvas(modifier = Modifier.size(40.dp)) {
            drawCircle(color = fillColor)

            // Small border inside the button bounds (does not grow the button)
            drawCircle(
                color = borderColor,
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )

            // Compass needle
            drawCompassNeedle(animatedDegrees, isNorthUp, needleColor, textMeasurer)
        }
    }
}

/**
 * Draw the compass needle.
 *
 * In "always north" mode: north half and "N" in the standard symbol color,
 * neutral south half. In "follow direction" mode: a compass-needle-like
 * triangle pointing in the travel direction whose base line is smaller than
 * its height, sized to 70% of the button. Rotated by [degrees] (0 = north up).
 */
private fun DrawScope.drawCompassNeedle(
    degrees: Float,
    isNorthUp: Boolean,
    needleColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radians = Math.toRadians(degrees.toDouble())

    // Unit direction toward the north tip (0° = up)
    val dirX = sin(radians).toFloat()
    val dirY = -cos(radians).toFloat()

    if (isNorthUp) {
        // Icon area is 24dp; needle spans ~20dp centered on the button
        val needleLength = 10.dp.toPx()
        val northX = centerX + dirX * needleLength
        val northY = centerY + dirY * needleLength
        val southX = centerX - dirX * needleLength
        val southY = centerY - dirY * needleLength

        // "Always north" mode: north half in symbol color, neutral south half
        drawLine(
            color = needleColor,
            start = Offset(centerX, centerY),
            end = Offset(northX, northY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        drawLine(
            color = needleColor.copy(alpha = 0.4f),
            start = Offset(centerX, centerY),
            end = Offset(southX, southY),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // "N" at north tip
        val textResult = textMeasurer.measure(
            text = "N",
            style = TextStyle(
                color = needleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            )
        )
        val nOffsetX = northX - textResult.size.width / 2f
        val nOffsetY = northY - textResult.size.height / 2f
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(nOffsetX, nOffsetY)
        )
    } else {
        // "Follow direction" mode: compass-needle-like triangle, base < height,
        // sized to 70% of the button (28dp on a 40dp visual)
        val height = 0.7f * size.minDimension
        val baseHalfWidth = height * 0.3125f // base ≈ 17.5dp < height = 28dp

        // Perpendicular to the travel direction
        val perpX = -dirY
        val perpY = dirX

        // Tip points in the travel direction; base sits behind the center
        val tipX = centerX + dirX * (height / 2f)
        val tipY = centerY + dirY * (height / 2f)
        val baseCenterX = centerX - dirX * (height / 2f)
        val baseCenterY = centerY - dirY * (height / 2f)

        val triangle = Path().apply {
            moveTo(tipX, tipY)
            lineTo(baseCenterX + perpX * baseHalfWidth, baseCenterY + perpY * baseHalfWidth)
            lineTo(baseCenterX - perpX * baseHalfWidth, baseCenterY - perpY * baseHalfWidth)
            close()
        }
        drawPath(path = triangle, color = needleColor)
    }
}
