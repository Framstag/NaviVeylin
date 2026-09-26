package com.naviveylin.ui.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naviveylin.R
import com.naviveylin.core.ProjectionUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPS fix quality indicator colors — the light presentation tones, used as the
 * button fill (Material tone-100 analogue). See the `CompassDarkFill*` constants for
 * the dark presentation tones and [compassFillColor] for the branch.
 */
private val GpsFillNoFix = Color(0xFFFFCDD2)   // light red
private val GpsFillPoorFix = Color(0xFFFFF9C4) // light yellow
private val GpsFillGoodFix = Color(0xFFC8E6C9) // light green

/**
 * Dark presentation tones of the same three hue families (Material tone-30
 * analogue). The hue family is presentation-independent — only the tone
 * changes — so a fix quality keeps its meaning in both schemes. Light fills on
 * a dark map glare and force a light `on*` role onto the needle (measured
 * 1.04:1, i.e. unreadable); these tones keep the needle/fill contrast at
 * 7.7-8.5:1 instead.
 */
private val CompassDarkFillNoFix = Color(0xFF93000A)   // dark red
private val CompassDarkFillPoorFix = Color(0xFF5C4300) // dark yellow
private val CompassDarkFillGoodFix = Color(0xFF1B4A24) // dark green

/** Symbol color on the light fills (needle, north label, rim). 11.7-15.4:1. */
private val CompassOnFillLight = Color(0xFF1F1F1F)

/** Symbol color on the dark fills (needle, north label, rim). 7.7-8.5:1. */
private val CompassOnFillDark = Color(0xFFE8EAED)

/**
 * Animated compass button showing north direction, GPS fix quality as the
 * button fill color, and supporting short-press (re-center) and long-press
 * (toggle orientation).
 *
 * The needle indicates geographic NORTH in every orientation mode — its screen
 * direction follows the map rotation only, never the vehicle heading (spec:
 * compass-button — Compass shows north direction). The widget takes no bearing
 * input by design, so a noisy/absent GPS bearing cannot move it.
 *
 * The needle, its north label and the rim come from the same per-presentation
 * palette as the fill (never from a theme color role), so their contrast
 * against the fill is guaranteed by construction and is wallpaper-independent
 * (spec: compass-button — Compass colors follow the resolved day/night
 * presentation).
 *
 * Larger than the other overlay buttons (56dp layout / 48dp visual vs
 * 48dp / 40dp) so it reads at a glance while driving, with the same shadow.
 *
 * @param mapAngleRadians Current map rotation in radians (0 = north up).
 * @param gpsFixQuality Current GPS fix quality for the fill color.
 * @param isDarkPresentation Resolved dark presentation (preference resolved with
 *   its environment signal). Drives the palette; deliberately NOT read from
 *   `isSystemInDarkTheme()` here, which would bypass a manual On/Off.
 * @param onCenterClick Called on short press to re-center on location.
 * @param onToggleOrientation Called on long press to toggle north-up / follow-direction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompassButton(
    mapAngleRadians: Double,
    gpsFixQuality: GpsFixQuality,
    isDarkPresentation: Boolean,
    onCenterClick: () -> Unit,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Needle rotation = screen direction of geographic north for the current map
    // rotation. Single convention lives in ProjectionUtils — never inline a
    // negation or a bearing term here (spec: compass-button).
    val targetDegrees = compassNeedleTarget(mapAngleRadians).toFloat()
    val animatedDegrees by animateFloatAsState(
        targetValue = targetDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "compassRotation"
    )

    val fillColor = compassFillColor(gpsFixQuality, isDarkPresentation)

    // Needle, north label and rim: one on-fill symbol color per presentation.
    val borderColor = compassOnFillColor(isDarkPresentation)
    val needleColor = borderColor
    val textMeasurer = rememberTextMeasurer()
    val compassLabel = stringResource(R.string.compass)
    val northLabel = stringResource(R.string.compass_north)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 56dp layout / 48dp visual — larger than the other overlay
            // buttons (48dp/40dp) so the compass reads at a glance while
            // driving (spec: compass-button — larger than other buttons).
            .size(56.dp)
            .shadow(3.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = compassLabel }
            .then(
                Modifier.combinedClickable(
                    onClick = onCenterClick,
                    onLongClick = onToggleOrientation
                )
            )
    ) {
        // 48dp visual fill, colored by GPS fix quality and presentation
        Canvas(modifier = Modifier.size(48.dp)) {
            drawCircle(color = fillColor)

            // Small border inside the button bounds (does not grow the button)
            drawCircle(
                color = borderColor,
                radius = size.minDimension / 2f - 1.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )

            // Compass needle
            drawCompassNeedle(animatedDegrees, needleColor, textMeasurer, northLabel)
        }
    }
}

/**
 * Button fill for a GPS fix quality: the quality's hue family in the tone of the
 * active presentation — red (no fix), yellow (poor), green (good) — spec:
 * compass-button, "GPS fix status fill color". The hue never changes with the
 * presentation, only the tone, so the quality stays recognizable.
 */
internal fun compassFillColor(gpsFixQuality: GpsFixQuality, isDarkPresentation: Boolean): Color =
    if (isDarkPresentation) {
        when (gpsFixQuality) {
            GpsFixQuality.NONE -> CompassDarkFillNoFix
            GpsFixQuality.POOR -> CompassDarkFillPoorFix
            GpsFixQuality.GOOD -> CompassDarkFillGoodFix
        }
    } else {
        when (gpsFixQuality) {
            GpsFixQuality.NONE -> GpsFillNoFix
            GpsFixQuality.POOR -> GpsFillPoorFix
            GpsFixQuality.GOOD -> GpsFillGoodFix
        }
    }

/**
 * Symbol color for the needle, the north label and the rim in the active
 * presentation: dark on the light fills, light on the dark fills. One value per
 * presentation keeps the contrast against every fix-quality fill above the 4.5:1
 * text threshold (11.7-15.4:1 light, 7.7-8.5:1 dark).
 */
internal fun compassOnFillColor(isDarkPresentation: Boolean): Color =
    if (isDarkPresentation) CompassOnFillDark else CompassOnFillLight

/**
 * Screen rotation (degrees, clockwise from screen-up) for the compass needle:
 * the direction in which north renders on the map, i.e.
 * [ProjectionUtils.compassRotationDegrees] of the map rotation.
 *
 * Deliberately a function of the map angle ALONE. The needle indicates north in
 * north-up and in follow-direction (heading-up) mode: a heading-up view stores
 * `angle = -bearing`, so driving south puts north at 180° (behind the vehicle)
 * and driving east puts it at 270° (the driver's left). The vehicle bearing is
 * not an input — the marker arrow and the map rotation already carry the travel
 * direction (spec: compass-button).
 */
internal fun compassNeedleTarget(mapAngleRadians: Double): Double =
    ProjectionUtils.compassRotationDegrees(mapAngleRadians)

/**
 * Draw the compass needle: north half and "N" in the standard symbol color,
 * neutral south half, rotated by [degrees] (0 = north up). One shape for every
 * orientation mode — the needle always means north.
 */
private fun DrawScope.drawCompassNeedle(
    degrees: Float,
    needleColor: Color,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    northLabel: String
) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val radians = Math.toRadians(degrees.toDouble())

    // Unit direction toward the north tip (0° = up)
    val dirX = sin(radians).toFloat()
    val dirY = -cos(radians).toFloat()

    // Icon area is 24dp; needle spans ~20dp centered on the button
    val needleLength = 10.dp.toPx()
    val northX = centerX + dirX * needleLength
    val northY = centerY + dirY * needleLength
    val southX = centerX - dirX * needleLength
    val southY = centerY - dirY * needleLength

    // North half in the symbol color, neutral south half
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
        text = northLabel,
        style = TextStyle(
            color = needleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        )
        val nOffsetX = northX - textResult.size.width / 2f
        val nOffsetY = northY - textResult.size.height / 2f
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(nOffsetX, nOffsetY)
        )
}
