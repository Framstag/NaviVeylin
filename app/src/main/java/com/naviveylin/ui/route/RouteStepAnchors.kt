package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry

/**
 * The map position of one route manoeuvre (spec: `route-analysis`).
 *
 * Produced by [instructionAnchors] from a route result; the position belongs to
 * the manoeuvre at the step with the same index in the step list.
 */
data class StepAnchor(
    val lat: Double,
    val lon: Double
)

/**
 * Whether a native description line is an instruction.
 *
 * The native description list starts with a `--- Route ---` header, which is not
 * an instruction and carries no manoeuvre, so it is excluded from the step list
 * and from the per-step positions alike.
 */
internal fun isInstructionLine(description: String): Boolean = !description.startsWith("---")

/**
 * Per-step manoeuvre positions of a route result, index-aligned with the step
 * list built from [RouteEntry.descriptions] (spec: `route-analysis`).
 *
 * Returns an empty list — never a shifted or partial one — when the route
 * carries no positions, or when the position arrays do not cover exactly the
 * instruction lines. A mismatch means the native alignment guard dropped the
 * positions; a partial list would move every later step to the wrong manoeuvre,
 * so no positions are better than wrong ones.
 */
fun instructionAnchors(route: RouteEntry): List<StepAnchor> {
    val descriptions = route.descriptions ?: return emptyList()
    val lats = route.instructionLats ?: return emptyList()
    val lons = route.instructionLons ?: return emptyList()

    val instructionCount = descriptions.count { isInstructionLine(it) }
    if (instructionCount == 0) return emptyList()
    if (lats.size != instructionCount || lons.size != instructionCount) return emptyList()

    return (0 until instructionCount).map { index -> StepAnchor(lats[index], lons[index]) }
}
