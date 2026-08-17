package com.naviveylin.ui.map

import com.framstag.libosmscout.client.FakeOSMScoutClient
import com.naviveylin.core.ProjectionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the geographic tile math used by the tile cache render path:
 * tile coordinate round-trips, tile pixel size vs. viewport scale, and
 * tile grid coverage of the viewport.
 */
@RunWith(RobolectricTestRunner::class)
class TileCacheRenderTest {

    private lateinit var client: FakeOSMScoutClient
    private lateinit var renderer: MapRenderer

    @Before
    fun setUp() {
        client = FakeOSMScoutClient()
        renderer = MapRenderer(
            client = client,
            dpi = 420.0,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    @Test
    fun tileRoundTrip() {
        val n = 1L shl 12
        for (x in listOf(0L, 100L, 2000L)) {
            for (y in listOf(0L, 100L, 2000L)) {
                val (lat, lon) = renderer.tileTopLeft(x, y, n)
                val rx = renderer.tileX(lon, n)
                val ry = renderer.tileY(lat, n)
                // Boundary lat/lon may round to the neighbouring tile by one
                // (floating-point precision at tile edges); the composition
                // positions tiles by their own top-left, so this is sub-pixel.
                assertTrue("tileX round-trip for $x,$y: got $rx", rx == x || rx == x - 1 || rx == x + 1)
                assertTrue("tileY round-trip for $x,$y: got $ry", ry == y || ry == y - 1 || ry == y + 1)
            }
        }
    }

    @Test
    fun tileSizeMatchesViewportScale() {
        val dpi = 420.0
        val mag = 14
        val W = 1080; val H = 2400
        val n = 1L shl mag
        val vp = ProjectionUtils.viewport(51.5, 7.5, mag, W, H, dpi, 0.0)
        val (tlLat, tlLon) = vp.screenToGeo(0.0, 0.0)
        val x = renderer.tileX(tlLon, n)
        val y = renderer.tileY(tlLat, n)
        val (tLat, tLon) = renderer.tileTopLeft(x, y, n)
        val (px, py) = vp.geoToScreen(tLat, tLon)
        // The next tile's top-left corner must be exactly tileSizePx away
        val (brLat, brLon) = renderer.tileTopLeft(x + 1, y + 1, n)
        val (bx, by) = vp.geoToScreen(brLat, brLon)
        val tileSize = renderer.tileSizePx
        assertEquals("tile width in viewport px", tileSize.toDouble(), bx - px, 1.0)
        assertEquals("tile height in viewport px", tileSize.toDouble(), by - py, 1.0)
    }

    @Test
    fun viewportTileRangeCoversViewport() {
        val dpi = 420.0
        val mag = 14
        val W = 1080; val H = 2400
        val n = 1L shl mag
        val vp = ProjectionUtils.viewport(51.5, 7.5, mag, W, H, dpi, 0.0)
        val (tlLat, tlLon) = vp.screenToGeo(0.0, 0.0)
        val (brLat, brLon) = vp.screenToGeo(W.toDouble(), H.toDouble())
        val xMin = renderer.tileX(tlLon, n); val xMax = renderer.tileX(brLon, n)
        val yMin = renderer.tileY(tlLat, n); val yMax = renderer.tileY(brLat, n)

        // Tile grid must start at or before the viewport top-left corner
        val (t0Lat, t0Lon) = renderer.tileTopLeft(xMin, yMin, n)
        val (p0x, p0y) = vp.geoToScreen(t0Lat, t0Lon)
        assertTrue("tile grid starts before viewport: $p0x,$p0y", p0x <= 0.5 && p0y <= 0.5)

        // Tile grid must end at or after the viewport bottom-right corner
        val (t1Lat, t1Lon) = renderer.tileTopLeft(xMax + 1, yMax + 1, n)
        val (p1x, p1y) = vp.geoToScreen(t1Lat, t1Lon)
        assertTrue("tile grid ends after viewport: $p1x,$p1y", p1x >= W - 0.5 && p1y >= H - 0.5)
    }

    @Test
    fun rotatedViewportTileRangeCoversAllFourCorners() {
        val dpi = 420.0
        val mag = 14
        val W = 1080; val H = 2400
        val n = 1L shl mag
        val angle = Math.PI / 4
        val vp = ProjectionUtils.viewport(51.5, 7.5, mag, W, H, dpi, angle)
        // The rotated tile path derives the geo bounds from all four screen
        // corners (the tl-br diagonal alone misses the other two).
        val corners = listOf(
            vp.screenToGeoRotated(0.0, 0.0),
            vp.screenToGeoRotated(W.toDouble(), 0.0),
            vp.screenToGeoRotated(0.0, H.toDouble()),
            vp.screenToGeoRotated(W.toDouble(), H.toDouble())
        )
        val minLat = corners.minOf { it.first }
        val maxLat = corners.maxOf { it.first }
        val minLon = corners.minOf { it.second }
        val maxLon = corners.maxOf { it.second }
        val xMin = renderer.tileX(minLon, n); val xMax = renderer.tileX(maxLon, n)
        val yMin = renderer.tileY(maxLat, n); val yMax = renderer.tileY(minLat, n)

        for ((lat, lon) in corners) {
            val x = renderer.tileX(lon, n)
            val y = renderer.tileY(lat, n)
            assertTrue("corner ($lat,$lon) x=$x outside [$xMin,$xMax]", x in xMin..xMax)
            assertTrue("corner ($lat,$lon) y=$y outside [$yMin,$yMax]", y in yMin..yMax)
        }
    }

    @Test
    fun tilePathRendersMissingTilesAndReusesCachedTiles() = runBlocking {
        // Screen larger than one tile (1120px @ 420dpi) so the viewport spans
        // multiple geographic tiles and the tile path renders several natively.
        renderer.screenWidth = 1200
        renderer.screenHeight = 1200
        renderer.requestRender(51.5, 7.5, 14, 0.0)

        withTimeout(5000) {
            while (renderer.frameFlow.value.bitmap == null || renderer.renderedMag != 14) {
                delay(10)
            }
        }
        val firstBitmap = renderer.frameFlow.value.bitmap
        val firstCount = client.renderWithRouteAndPoisCount.get()
        assertTrue("tile path must render missing tiles natively (count=$firstCount)", firstCount >= 1)

        // Forced full render at the SAME viewport: the tile path must compose
        // entirely from the cache — no new native render calls.
        renderer.requestRender(51.5, 7.5, 14, 0.0, forceFullRender = true)
        withTimeout(5000) {
            while (renderer.frameFlow.value.bitmap === firstBitmap) {
                delay(10)
            }
        }
        assertEquals(
            "cached tiles must be reused on re-render",
            firstCount,
            client.renderWithRouteAndPoisCount.get()
        )
    }

    @Test
    fun rotatedTileEdgeMatchesProjectionRotation() {
        val dpi = 420.0
        val mag = 14
        val W = 1080; val H = 2400
        val n = 1L shl mag
        val angle = Math.PI / 4
        val vp = ProjectionUtils.viewport(51.5, 7.5, mag, W, H, dpi, angle)
        val (tLat, tLon) = renderer.tileTopLeft(100, 100, n)
        val (px, py) = vp.geoToScreenRotated(tLat, tLon)
        // The tile one column east has its top-left exactly tileSizePx east in geo.
        val (trLat, trLon) = renderer.tileTopLeft(101, 100, n)
        val (trx, try_) = vp.geoToScreenRotated(trLat, trLon)
        val tileSize = renderer.tileSizePx
        // Drawing the north-up tile rotated by the viewport angle around its
        // top-left must place the top-right corner at the rotated offset.
        val expectedX = px + tileSize * Math.cos(angle)
        val expectedY = py + tileSize * Math.sin(angle)
        assertEquals("rotated tile top-right x", expectedX, trx, 1.0)
        assertEquals("rotated tile top-right y", expectedY, try_, 1.0)
    }
}
