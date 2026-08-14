package com.naviveylin.test

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Parsed GPX track point.
 */
data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val time: Long? = null
)

/**
 * Lightweight GPX 1.0/1.1 track parser using standard Java XML.
 * Extracts all track points from `<trkpt>` elements across all segments.
 *
 * Usage:
 * ```
 * val track = GpxParser.parse(inputStream)
 * for (pt in track) {
 *     navigationViewModel.processLocation(
 *         pt.lat, pt.lon, speed = 0.0,
 *         accuracy = 10.0, timestamp = pt.time ?: System.currentTimeMillis()
 *     )
 * }
 * ```
 */
object GpxParser {

    private val isoFormat8601 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    private val isoFormat8601withMillis = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    /**
     * Parse a GPX input stream and return all track points in document order.
     */
    fun parse(input: InputStream): List<GpxPoint> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)
        doc.documentElement.normalize()

        val points = mutableListOf<GpxPoint>()
        val trkptNodes = doc.getElementsByTagName("trkpt")

        for (i in 0 until trkptNodes.length) {
            val element = trkptNodes.item(i) as Element
            val lat = element.getAttribute("lat").toDouble()
            val lon = element.getAttribute("lon").toDouble()

            val elevation = getChildText(element, "ele")?.toDoubleOrNull()
            val time = getChildText(element, "time")?.let { parseIso8601(it) }

            points.add(GpxPoint(lat, lon, elevation, time))
        }

        return points
    }

    /** Get text content of first child element with given tag name. */
    private fun getChildText(parent: Element, tagName: String): String? {
        val nodes = parent.getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Parse ISO 8601 timestamp to epoch millis. */
    private fun parseIso8601(text: String): Long? {
        return try {
            val cleaned = text.trim().removeSuffix("Z") + "Z"
            // Try with millis first
            try {
                isoFormat8601withMillis.parse(cleaned)?.time
            } catch (_: Exception) {
                isoFormat8601.parse(cleaned)?.time
            }
        } catch (_: Exception) {
            null
        }
    }
}
