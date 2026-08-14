package com.naviveylin.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream

class GpxParserTest {

    private val sampleGpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test">
  <trk>
    <name>Test Track</name>
    <trkseg>
      <trkpt lat="52.5200" lon="13.4050">
        <ele>35.0</ele>
        <time>2024-01-15T10:00:00Z</time>
      </trkpt>
      <trkpt lat="52.5210" lon="13.4060">
        <ele>36.0</ele>
        <time>2024-01-15T10:00:10Z</time>
      </trkpt>
      <trkpt lat="52.5220" lon="13.4070">
        <ele>37.0</ele>
        <time>2024-01-15T10:00:20Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>"""

    @Test
    fun `parse GPX with three track points`() {
        val points = GpxParser.parse(ByteArrayInputStream(sampleGpx.toByteArray()))
        assertEquals(3, points.size)
    }

    @Test
    fun `parse first point coordinates`() {
        val points = GpxParser.parse(ByteArrayInputStream(sampleGpx.toByteArray()))
        assertEquals(52.5200, points[0].lat, 0.0001)
        assertEquals(13.4050, points[0].lon, 0.0001)
    }

    @Test
    fun `parse elevation`() {
        val points = GpxParser.parse(ByteArrayInputStream(sampleGpx.toByteArray()))
        assertEquals(35.0, points[0].elevation!!, 0.1)
        assertEquals(37.0, points[2].elevation!!, 0.1)
    }

    @Test
    fun `parse timestamp`() {
        val points = GpxParser.parse(ByteArrayInputStream(sampleGpx.toByteArray()))
        assertNotNull("Timestamp should be parsed", points[0].time)
        // 2024-01-15T10:00:00Z = 1705312800000 ms
        assertEquals(1705312800000L, points[0].time)
    }

    @Test
    fun `parse GPX without elevation or time`() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <trkseg>
      <trkpt lat="48.8566" lon="2.3522"></trkpt>
      <trkpt lat="48.8570" lon="2.3530"></trkpt>
    </trkseg>
  </trk>
</gpx>"""
        val points = GpxParser.parse(ByteArrayInputStream(gpx.toByteArray()))
        assertEquals(2, points.size)
        assert(points[0].elevation == null) { "elevation should be null" }
        assert(points[0].time == null) { "time should be null" }
    }

    @Test
    fun `parse empty GPX`() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <trkseg>
    </trkseg>
  </trk>
</gpx>"""
        val points = GpxParser.parse(ByteArrayInputStream(gpx.toByteArray()))
        assertEquals(0, points.size)
    }

    @Test
    fun `parse GPX with multiple segments`() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1">
  <trk>
    <trkseg>
      <trkpt lat="1.0" lon="2.0"></trkpt>
    </trkseg>
    <trkseg>
      <trkpt lat="3.0" lon="4.0"></trkpt>
      <trkpt lat="5.0" lon="6.0"></trkpt>
    </trkseg>
  </trk>
</gpx>"""
        val points = GpxParser.parse(ByteArrayInputStream(gpx.toByteArray()))
        assertEquals(3, points.size)
        assertEquals(1.0, points[0].lat, 0.0001)
        assertEquals(3.0, points[1].lat, 0.0001)
        assertEquals(5.0, points[2].lat, 0.0001)
    }
}
