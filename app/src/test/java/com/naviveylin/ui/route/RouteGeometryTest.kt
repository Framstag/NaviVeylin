package com.naviveylin.ui.route

import com.framstag.libosmscout.client.RouteEntry
import com.naviveylin.core.DiagnosticsLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The one geometry rule every publisher of route geometry goes through (spec:
 * `route-map-overview` — Degenerate route geometry degrades safely: absent counts as an empty
 * polyline). Default Robolectric sandbox: no `@Config` here.
 */
@RunWith(RobolectricTestRunner::class)
class RouteGeometryTest {

    @After
    fun tearDown() {
        DiagnosticsLog.reset()
    }

    private fun route(lats: DoubleArray?, lons: DoubleArray?): RouteEntry = RouteEntry().apply {
        routeHandle = 1L
        latitudes = lats
        longitudes = lons
        distance = 5000.0
        descriptions = arrayOf("Start: A  [0.0 km]", "Left onto B  [1.2 km]")
    }

    @Test
    fun `absent coordinates publish no adopted geometry`() {
        assertNull(adoptedRouteGeometry(route(null, null)))
    }

    @Test
    fun `one absent coordinate array publishes no adopted geometry`() {
        assertNull(adoptedRouteGeometry(route(doubleArrayOf(52.52, 52.53), null)))
        assertNull(adoptedRouteGeometry(route(null, doubleArrayOf(13.40, 13.41))))
    }

    @Test
    fun `a single point publishes no adopted geometry`() {
        assertNull(adoptedRouteGeometry(route(doubleArrayOf(52.52), doubleArrayOf(13.40))))
    }

    @Test
    fun `a length mismatch publishes no adopted geometry`() {
        assertNull(
            adoptedRouteGeometry(
                route(doubleArrayOf(52.52, 52.53, 52.54), doubleArrayOf(13.40, 13.41))
            )
        )
    }

    @Test
    fun `usable coordinates publish the polyline ends as start and destination`() {
        val result = adoptedRouteGeometry(
            route(doubleArrayOf(52.52, 52.53, 52.54), doubleArrayOf(13.40, 13.41, 13.42))
        )

        assertEquals(52.52, result!!.startLat, 1e-9)
        assertEquals(13.40, result.startLon, 1e-9)
        assertEquals(52.54, result.destLat, 1e-9)
        assertEquals(13.42, result.destLon, 1e-9)
        assertEquals(3, result.routeLats.size)
    }

    @Test
    fun `a calculation always publishes, with the requested endpoints`() {
        val result = calculatedRouteGeometry(
            route(doubleArrayOf(52.52, 52.53), doubleArrayOf(13.40, 13.41)),
            startLat = 51.0, startLon = 7.0, destLat = 50.0, destLon = 8.0
        )

        assertEquals(51.0, result.startLat, 1e-9)
        assertEquals(7.0, result.startLon, 1e-9)
        assertEquals(50.0, result.destLat, 1e-9)
        assertEquals(8.0, result.destLon, 1e-9)
        assertEquals(2, result.routeLats.size)
    }

    @Test
    fun `a calculation without coordinates publishes an empty polyline with the endpoints`() {
        // The camera fallback needs this result: absent coordinates behave exactly like an empty
        // polyline (spec: `route-map-overview` — Empty polyline falls back to endpoints).
        val result = calculatedRouteGeometry(
            route(null, null),
            startLat = 51.0, startLon = 7.0, destLat = 50.0, destLon = 8.0
        )

        assertEquals(0, result.routeLats.size)
        assertEquals(0, result.routeLons.size)
        assertEquals(51.0, result.startLat, 1e-9)
        assertEquals(50.0, result.destLat, 1e-9)
    }

    @Test
    fun `an absent geometry is recorded as a coordinate-free diagnostics line`() {
        adoptedRouteGeometry(route(null, null))
        calculatedRouteGeometry(
            route(null, null),
            startLat = 51.0, startLon = 7.0, destLat = 50.0, destLon = 8.0
        )

        val line = DiagnosticsLog.readEntries().last { it.contains(DiagnosticsLog.ROUTE_TAG) }
        val message = line.substringAfter("${DiagnosticsLog.ROUTE_TAG} ")
        assertTrue("the line names the reason: $line", message.contains("route geometry absent"))
        assertTrue("the line names the shape: $line", message.contains("polyline=absent"))
        assertFalse(
            "no coordinate-shaped token may reach the line: $line",
            Regex("""-?\d+\.\d{3,}""").containsMatchIn(message)
        )
    }
}
