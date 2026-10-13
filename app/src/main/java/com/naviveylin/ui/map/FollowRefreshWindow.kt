package com.naviveylin.ui.map

import kotlin.math.abs

/**
 * The values the follow display loop feeds into [com.naviveylin.core.FollowRefreshPolicy]
 * (spec: `smooth-follow` — Follow frame refresh before the overrun margin is exhausted; design D2).
 *
 * Pure functions so the loop's inputs are testable without a device: the loop's decision is the
 * policy's, but the numbers it hands over come from the frame in hand and the canvas.
 *
 * The overrun is 1.2x the surface, so the two axes do NOT have the same margin — on a 1080x2400
 * canvas the frame offers 108 px horizontally and 240 px vertically, and the reproduction's drift
 * (the vehicle's travel, up-screen) saturates at the 240 px one. The decision therefore has to use
 * the margin of the axis the drift is actually on: the pair returned here is that axis's drift
 * magnitude and its margin.
 */
internal fun followRefreshWindow(
    rawOffsetX: Double,
    rawOffsetY: Double,
    bitmapW: Int,
    bitmapH: Int,
    canvasW: Int,
    canvasH: Int
): Pair<Double, Double> {
    val marginX = ((bitmapW - canvasW) / 2.0).coerceAtLeast(0.0)
    val marginY = ((bitmapH - canvasH) / 2.0).coerceAtLeast(0.0)
    val usedX = if (marginX > 0.0) abs(rawOffsetX) / marginX else Double.POSITIVE_INFINITY
    val usedY = if (marginY > 0.0) abs(rawOffsetY) / marginY else Double.POSITIVE_INFINITY
    return if (usedX >= usedY) abs(rawOffsetX) to marginX else abs(rawOffsetY) to marginY
}
