package com.naviveylin.auto

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [SurfaceAttribution] (spec: osm-attribution — "Attribution shown
 * in Android Auto variant"). Default Robolectric sandbox, no @Config.
 */
@RunWith(RobolectricTestRunner::class)
class SurfaceAttributionTest {

    private val density = 1.0f

    @Test
    fun geometrySitsInBottomRightCorner() {
        val rect = SurfaceAttribution.geometry(Rect(), 1920, 1080, density)!!
        assertEquals(1920 - 8, rect.right)
        assertEquals(1080 - 8, rect.bottom)
        assertTrue(rect.left > 0)
        assertTrue(rect.top > 0)
    }

    @Test
    fun geometryAnchorsToStableBoundsBottom() {
        val stable = Rect(0, 0, 1920, 1000)
        val rect = SurfaceAttribution.geometry(stable, 1920, 1080, density)!!
        assertEquals(1000 - 8, rect.bottom)
    }

    @Test
    fun geometryNullWhenSurfaceTooSmall() {
        assertNull(SurfaceAttribution.geometry(Rect(), 10, 10, density))
    }

    @Test
    fun geometryScalesWithDensity() {
        val low = SurfaceAttribution.geometry(Rect(), 1920, 1080, 1.0f)!!
        val high = SurfaceAttribution.geometry(Rect(), 1920, 1080, 2.0f)!!
        assertTrue(high.width() > low.width())
        assertTrue(high.height() > low.height())
    }
}
