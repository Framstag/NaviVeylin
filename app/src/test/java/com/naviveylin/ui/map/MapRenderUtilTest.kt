package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.MapRenderUtil
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [MapRenderUtil.renderToBitmap].
 */
@RunWith(RobolectricTestRunner::class)
class MapRenderUtilTest {

    private val client = FakeOSMScoutClient()

    @Test
    fun renderToBitmapReturnsBitmap() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5
        )
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width > 0)
        assertTrue(bitmap.height > 0)
    }

    @Test
    fun renderToBitmapWithOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5,
            routeLats = doubleArrayOf(48.85, 48.86),
            routeLons = doubleArrayOf(2.35, 2.36),
            favoriteLats = doubleArrayOf(48.86),
            favoriteLons = doubleArrayOf(2.35)
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapWithSearchSelected() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5,
            searchSelLat = 48.86,
            searchSelLon = 2.35
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapWithTrack() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5,
            trackLats = doubleArrayOf(48.85, 48.86),
            trackLons = doubleArrayOf(2.35, 2.36)
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapNullOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5,
            routeLats = null,
            routeLons = null,
            favoriteLats = null,
            favoriteLons = null
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapEmptyOverlays() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 100,
            height = 100,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5,
            routeLats = doubleArrayOf(),
            routeLons = doubleArrayOf(),
            favoriteLats = doubleArrayOf(),
            favoriteLons = doubleArrayOf()
        )
        assertNotNull(bitmap)
    }

    @Test
    fun renderToBitmapReturnsCorrectSize() {
        val bitmap = MapRenderUtil.renderToBitmap(
            client = client,
            width = 200,
            height = 150,
            lat = 48.8566,
            lon = 2.3522,
            angle = 0.0,
            magnification = 5
        )
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width == 200)
        assertTrue(bitmap.height == 150)
    }
}
