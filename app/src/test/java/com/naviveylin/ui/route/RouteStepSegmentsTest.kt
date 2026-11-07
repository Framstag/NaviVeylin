package com.naviveylin.ui.route

import com.naviveylin.core.routeRows
import com.framstag.libosmscout.client.TurnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the step-to-polyline mapping (spec: `route-analysis`).
 *
 * A step owns the leg that **leads to** its manoeuvre: the native description's
 * `[x km, y min]` is the cumulative distance at the manoeuvre's own node minus the
 * previous node's, so the described leg ends where the instruction happens (owner
 * finding, 2026-10-03: the highlighted leg used to be the one *after* the manoeuvre).
 *
 * The polyline point lists below are hand-written so the expected vertex indices
 * are readable in the assertions.
 */
class RouteStepSegmentsTest {

    /** North-south polyline with 4 vertices at 0.001 deg steps. */
    private val straightLats = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
    private val straightLons = doubleArrayOf(0.0, 0.001, 0.002, 0.003)

    /**
     * Route that visits the same area twice: it runs north, spurts east, continues
     * north, then comes back south-west to a point near its own earlier leg.
     * Vertex 4 is spatially close to vertex 1.
     */
    private val foldedLats = doubleArrayOf(0.0, 0.0, 0.0002, 0.0, 0.0)
    private val foldedLons = doubleArrayOf(0.0, 0.001, 0.001, 0.0018, 0.0006)

    @Test
    fun `straight route maps each step to the leg leading to its manoeuvre`() {
        val anchors = listOf(StepAnchor(0.0, 0.0), StepAnchor(0.0, 0.002), StepAnchor(0.0, 0.003))

        // Step 0 is the start line on vertex 0: no leg. Step 1's manoeuvre is on vertex 2, so its
        // leg is 0..2; step 2's leg is 2..3.
        assertEquals(
            listOf(IntRange.EMPTY, 0..2, 2..3),
            stepSegments(straightLats, straightLons, 3, anchors)
        )
    }

    @Test
    fun `folded route does not match the earlier visit of the same area`() {
        // Anchor 2 sits nearest to vertex 1 (the earlier visit) — only the
        // monotonic search may pick the later visit at vertex 4.
        val anchors = listOf(
            StepAnchor(0.0, 0.0002),
            StepAnchor(0.0, 0.0017),
            StepAnchor(0.0, 0.0009)
        )

        assertEquals(
            listOf(IntRange.EMPTY, 0..3, 3..4),
            stepSegments(foldedLats, foldedLons, 3, anchors)
        )
    }

    @Test
    fun `a single row covers the whole polyline`() {
        // One row is the start and the arrival at once: it owns the polyline from its own boundary,
        // the route's first vertex, to the route's last.
        assertEquals(
            listOf(0..3),
            stepSegments(straightLats, straightLons, 1, listOf(StepAnchor(0.0, 0.0009)))
        )
    }

    @Test
    fun `two manoeuvres on the same vertex leave the later row with the tail`() {
        val anchors = listOf(StepAnchor(0.0, 0.0009), StepAnchor(0.0, 0.0009))

        // The later row is the arrival: its own boundary does not move, but the route's tail still
        // belongs to it, so the rows tile the polyline to its end.
        assertEquals(
            listOf(0..1, 1..3),
            stepSegments(straightLats, straightLons, 2, anchors)
        )
    }

    @Test
    fun `a step without a position yields no range`() {
        val anchors = listOf(StepAnchor(0.0, 0.0))

        assertEquals(
            listOf(IntRange.EMPTY, IntRange.EMPTY),
            stepSegments(straightLats, straightLons, 2, anchors)
        )
    }

    @Test
    fun `no anchors yields no ranges at all`() {
        val segments = stepSegments(straightLats, straightLons, 3, emptyList())

        assertEquals(3, segments.size)
        assertTrue(segments.all { it == IntRange.EMPTY })
    }

    @Test
    fun `empty polyline yields no ranges`() {
        val segments = stepSegments(doubleArrayOf(), doubleArrayOf(), 2, listOf(StepAnchor(0.0, 0.0)))

        assertEquals(listOf(IntRange.EMPTY, IntRange.EMPTY), segments)
    }

    @Test
    fun `latitude and longitude lists of different lengths use the common prefix`() {
        val segments = stepSegments(
            doubleArrayOf(0.0, 0.0, 0.0),
            doubleArrayOf(0.0, 0.001),
            1,
            listOf(StepAnchor(0.0, 0.0009))
        )

        assertEquals(listOf(0..1), segments)
    }

    @Test
    fun `no steps yields no ranges`() {
        assertTrue(stepSegments(straightLats, straightLons, 0, emptyList()).isEmpty())
    }

    /**
     * The shape the bridge publishes for a snapped start (change `publish-requested-route-start`): the
     * polyline's first vertex is the **requested** start and the second is the snapped node, so the start
     * line's own row is anchored on vertex 0 and the first turn's leg begins there too.
     *
     * The anchors come from the real row pipeline ([routeRows] → [rowAnchors]) rather than from a hand-made
     * list, because the row that has to line up with vertex 0 is the one that pipeline produces.
     */
    @Test
    fun `aPrependedStartAnchorsStepZeroAndOwnsNoLeg`() {
        // Vertex 0 is the prepended requested start, vertex 1 the snapped node the router chose, then the
        // routed geometry to the manoeuvre and the arrival.
        val lats = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
        val lons = doubleArrayOf(0.0, 0.0002, 0.001, 0.002, 0.003)

        val steps = listOf(
            startStep(lat = 0.0, lon = 0.0),
            turnStep(TurnType.RIGHT, lat = 0.0, lon = 0.002, legDistance = 200.0),
            targetStep(lat = 0.0, lon = 0.003, legDistance = 100.0)
        )
        val anchors = rowAnchors(routeRows(steps))

        // The derived rows are the start line, the turn and the arrival — index-aligned with the anchors,
        // and the first anchor is the published start (vertex 0), not the snapped node.
        assertEquals(3, anchors.size)
        assertEquals(0.0, anchors[0].lat, 0.0)
        assertEquals(0.0, anchors[0].lon, 0.0)

        // Step 0 owns no leg; the first turn's leg starts at the prepended start, so its segment covers
        // the approach together with the routed run to its own manoeuvre.
        assertEquals(
            listOf(IntRange.EMPTY, 0..3, 3..4),
            stepSegments(lats, lons, anchors.size, anchors)
        )
    }
}
