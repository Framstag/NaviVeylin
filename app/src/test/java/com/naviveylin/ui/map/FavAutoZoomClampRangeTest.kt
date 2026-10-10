package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Holds `fav-auto-zoom`'s stated clamp range to the range `computeAreaZoom` applies
 * (spec: fav-auto-zoom — Bounding box zoom calculation, scenario "Magnitude clamped to
 * valid range").
 *
 * The scenario is the one place in that spec that writes the clamp's numbers down, and it
 * claimed **4–18** while the fit clamps to the area-favorites floor / `MAX_MAG` (14–20) —
 * a divergence that survived from the initial import (code `coerceIn(MIN_AREA_ZOOM, MAX_MAG)`)
 * until this case, because nothing compared the two artefacts: the fit's own cases pin the
 * behaviour, and the sibling specs pin the app-wide range as 4–20.
 *
 * The case reads the two bounds **from the code** (the floor through a bbox whose raw fit is
 * below it, the ceiling through the size range over which the fit stops moving) and compares
 * them to the numbers the scenario states, so no bound is written down twice and a change to
 * either artefact fails here. It reads the spec file, the way `MapMenuBackOrderComposeTest`
 * reads the band source order: the working directory of an `:app` unit test is the module
 * directory. Both ranges are printed before the assertion, so the JUnit XML's `system-out`
 * carries the measurement of the run that made it.
 *
 * Robolectric with the default sandbox (AGENTS.md classloader rule for the Android `ViewModel`
 * companion); the `dpi` stays at `ProjectionUtils.REFERENCE_DPI`, so no density is read here.
 */
@RunWith(RobolectricTestRunner::class)
class FavAutoZoomClampRangeTest {

    private val specFile = File("../openspec/specs/fav-auto-zoom/spec.md")

    private val width = 400
    private val height = 800

    @Test
    fun `the clamp scenario states the range the area favorite fit applies`() {
        val enforced = enforcedClampRange()
        val stated = statedClampRange()
        println("clampRange spec=[${stated.first}, ${stated.second}] enforced=[${enforced.first}, ${enforced.second}]")

        assertEquals(
            "the scenario's lower bound must be the floor the area-favorite fit clamps to",
            enforced.first, stated.first, 0.0
        )
        assertEquals(
            "the scenario's upper bound must be the ceiling the fit clamps to",
            enforced.second, stated.second, 0.0
        )
    }

    /**
     * The clamp's two ends, read from the code rather than from a constant.
     *
     * The floor is the one the area-favorite fit passes: `computeAreaZoom`'s default `minZoom`,
     * the value `onFavoriteSelected` hands the fit explicitly. A bbox spanning 1000 km reaches it
     * (its raw fit, read with the floor parameter lifted to 0, is below the clamped answer), so
     * the floor is active and not a coincidental result.
     *
     * The ceiling is reached too: a 1 m bbox and one 64x smaller return the **same** value — a
     * computed fit would climb six whole levels across that size range — while a 1000 m bbox fits
     * at a lower level without touching either clamp.
     */
    private fun enforcedClampRange(): Pair<Double, Double> {
        val huge = bboxMeters(1_000_000.0)
        val rawHuge = MapCanvasViewModel.computeAreaZoom(huge, width, height, minZoom = 0.0)
        val floor = MapCanvasViewModel.computeAreaZoom(huge, width, height)
        assertTrue(
            "premise: the huge bbox's raw fit lies below the clamped floor ($rawHuge -> $floor)",
            rawHuge < floor
        )

        val ceiling = MapCanvasViewModel.computeAreaZoom(bboxMeters(1.0), width, height)
        for (factor in listOf(4.0, 16.0, 64.0)) {
            assertEquals(
                "premise: the ceiling is a clamp, not a fit (a ${factor}x smaller bbox must not fit closer)",
                ceiling, MapCanvasViewModel.computeAreaZoom(bboxMeters(1.0 / factor), width, height), 0.0
            )
        }
        val unclamped = MapCanvasViewModel.computeAreaZoom(bboxMeters(1_000.0), width, height)
        assertTrue("premise: the ceiling is the ceiling, not a value no fit can stay below ($unclamped)", unclamped < ceiling)

        return floor to ceiling
    }

    /**
     * The two bounds the scenario states, read from its `- **WHEN**` line. Every number on that
     * line is a bound, so a line with more or fewer than two numbers is a planning defect rather
     * than a bound to compare.
     */
    private fun statedClampRange(): Pair<Double, Double> {
        assertTrue("the spec file must exist at ${specFile.absolutePath}", specFile.isFile)
        val text = specFile.readText()
        val whenLine = Regex(
            Regex.escape("#### Scenario: Magnitude clamped to valid range") + "\\s*\\n- \\*\\*WHEN\\*\\*([^\\n]*)"
        ).find(text)?.groupValues?.get(1)
        assertNotNull(
            "the spec must carry the scenario \"Magnitude clamped to valid range\": ${specFile.absolutePath}",
            whenLine
        )
        val bounds = Regex("([0-9]+(?:\\.[0-9]+)?)")
            .findAll(whenLine.orEmpty()).map { it.groupValues[1].toDouble() }.toList()
        assertEquals("the scenario must name exactly its two clamp bounds: \"$whenLine\"", 2, bounds.size)
        return bounds[0] to bounds[1]
    }

    /** A bbox spanning [meters] x [meters] of ground distance around Dortmund, centred on a point. */
    private fun bboxMeters(meters: Double): DoubleArray {
        val lat = 51.5136
        val lon = 7.4653
        val dLat = meters / METERS_PER_DEG_LAT
        val dLon = meters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(lat)))
        return doubleArrayOf(lat - dLat / 2, lat + dLat / 2, lon - dLon / 2, lon + dLon / 2)
    }

    private companion object {
        /** Meters per degree of latitude, as the fit's own math uses it. */
        const val METERS_PER_DEG_LAT = 111320.0
    }
}
