package com.naviveylin.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone map screen's layer band table (spec: map-canvas-screen — Phone map overlay layer stack).
 *
 * The screen's stacking order is a checked fact rather than a comment: these cases fail if a band is
 * added out of order, renumbered onto another band's value, or renamed without keeping a distinct
 * layer tag.
 */
class MapLayerBandsTest {

    @Test
    fun `the bands are declared back to front`() {
        assertEquals(
            listOf(
                MapLayer.MAP, MapLayer.CHROME, MapLayer.MENU, MapLayer.MODAL, MapLayer.SNACKBAR
            ),
            MapLayer.backToFront
        )
    }

    @Test
    fun `the band values strictly increase from the map to the snackbar`() {
        val values = MapLayer.backToFront.map { it.z }
        // Every band's value is above the one before it, so the stacking order is the band order.
        MapLayer.backToFront.zipWithNext { back, front ->
            assertTrue(
                "band ${front.name} (z=${front.z}) must be composed above ${back.name} (z=${back.z})",
                front.z > back.z
            )
        }
        // No two bands may share a value: a shared value would make the stacking order arbitrary.
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `a band can be inserted between two existing bands without renumbering`() {
        MapLayer.backToFront.zipWithNext { back, front ->
            assertTrue(
                "no room to insert a band between ${back.name} and ${front.name}",
                front.z - back.z >= 2f
            )
        }
    }

    @Test
    fun `each band carries its own layer tag`() {
        val tags = MapLayer.backToFront.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        assertTrue("a layer tag must name its layer", tags.all { it.startsWith("map-layer-") })
    }
}
