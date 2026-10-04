package com.naviveylin.ui.route

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
    fun `a single step owns the leg from the route start to its manoeuvre`() {
        assertEquals(
            listOf(0..1),
            stepSegments(straightLats, straightLons, 1, listOf(StepAnchor(0.0, 0.0009)))
        )
    }

    @Test
    fun `two manoeuvres on the same vertex leave the later step without a leg`() {
        val anchors = listOf(StepAnchor(0.0, 0.0009), StepAnchor(0.0, 0.0009))

        assertEquals(
            listOf(0..1, IntRange.EMPTY),
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
}
