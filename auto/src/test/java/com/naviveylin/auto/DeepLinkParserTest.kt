package com.naviveylin.auto

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkParserTest {

    // --- geo URIs ---

    @Test
    fun geo_plainCoordinates() {
        val dest = DeepLinkParser.parseUri("geo:48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertNull(dest.query)
    }

    @Test
    fun geo_coordinatesWithLabel() {
        val dest = DeepLinkParser.parseUri("geo:48.8566,2.3522?q=Eiffel%20Tower")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun geo_zeroZeroWithCoordinatesInQuery() {
        val dest = DeepLinkParser.parseUri("geo:0,0?q=48.8566,2.3522(Eiffel%20Tower)")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun geo_zeroZeroWithAddressQuery() {
        val dest = DeepLinkParser.parseUri("geo:0,0?q=Berlin%20Hauptbahnhof")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertNull(dest.lon)
        assertEquals("Berlin Hauptbahnhof", dest.query)
    }

    @Test
    fun geo_queryWithUnencodedCoordinates() {
        val dest = DeepLinkParser.parseUri("geo:0,0?q=48.8566, 2.3522 Paris")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    // --- Google Maps URLs ---

    @Test
    fun mapsUrl_qCoordinates() {
        val dest = DeepLinkParser.parseUri("https://maps.google.com/?q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_qAddress() {
        val dest = DeepLinkParser.parseUri("https://maps.google.com/?q=Eiffel+Tower")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun mapsUrl_daddrCoordinates() {
        val dest = DeepLinkParser.parseUri("https://www.google.com/maps/dir//48.8566,2.3522/@48.8566,2.3522,15z")
        // daddr only present in /dir/ URLs with params; here path has no params — parse host check
        assertNull(dest)
    }

    @Test
    fun mapsUrl_daddrQueryParam() {
        val dest = DeepLinkParser.parseUri("https://maps.google.com/?daddr=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_destinationQueryParam() {
        val dest = DeepLinkParser.parseUri("https://maps.google.com/?destination=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_shortLink() {
        val dest = DeepLinkParser.parseUri("https://maps.app.goo.gl/abc123?q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_wrongHostRejected() {
        assertNull(DeepLinkParser.parseUri("https://example.com/?q=48.8566,2.3522"))
    }

    // --- google.navigation scheme ---

    @Test
    fun googleNavigation_qCoordinates() {
        val dest = DeepLinkParser.parseUri("google.navigation:q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun googleNavigation_qAddress() {
        val dest = DeepLinkParser.parseUri("google.navigation:q=Eiffel+Tower")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    // --- Intent-level parsing ---

    @Test
    fun intent_geoData() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        val dest = DeepLinkParser.parse(intent)
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun intent_extraTextCoordinates() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "48.8566, 2.3522")
        }
        val dest = DeepLinkParser.parse(intent)
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun intent_extraTextAddress() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Brandenburg Gate Berlin")
        }
        val dest = DeepLinkParser.parse(intent)
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Brandenburg Gate Berlin", dest.query)
    }

    @Test
    fun intent_extraQuery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            putExtra("android.intent.extra.QUERY", "Kölner Dom")
        }
        val dest = DeepLinkParser.parse(intent)
        assertNotNull(dest)
        assertEquals("Kölner Dom", dest!!.query)
    }

    @Test
    fun intent_extraTextWinsOverQuery() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "48.8566, 2.3522")
            putExtra("android.intent.extra.QUERY", "ignore me")
        }
        val dest = DeepLinkParser.parse(intent)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
    }

    // --- malformed input ---

    @Test
    fun nullIntent() {
        assertNull(DeepLinkParser.parse(null))
    }

    @Test
    fun nullUri() {
        assertNull(DeepLinkParser.parseUri(null))
        assertNull(DeepLinkParser.parseUri(""))
        assertNull(DeepLinkParser.parseUri("   "))
    }

    @Test
    fun unsupportedScheme() {
        assertNull(DeepLinkParser.parseUri("ftp://host/file"))
    }

    @Test
    fun geoWithoutCoordinates() {
        assertNull(DeepLinkParser.parseUri("geo:"))
    }

    @Test
    fun invalidCoordinates() {
        assertNull(DeepLinkParser.parseUri("geo:abc,def"))
        assertNull(DeepLinkParser.parseCoordinates("not coords"))
        assertNull(DeepLinkParser.parseCoordinates("48.8566")) // single value
    }

    @Test
    fun outOfRangeCoordinates() {
        assertNull(DeepLinkParser.parseCoordinates("91,0"))
        assertNull(DeepLinkParser.parseCoordinates("0,181"))
    }

    @Test
    fun emptyIntent() {
        assertNull(DeepLinkParser.parse(Intent()))
    }

    // --- coordinates extraction helpers ---

    @Test
    fun extractLabeledCoordinates() {
        val (lat, lon, label) = DeepLinkParser.extractCoordinatesFromQuery("48.8566,2.3522(Eiffel Tower)")
        assertEquals(48.8566, lat!!, 1e-6)
        assertEquals(2.3522, lon!!, 1e-6)
        assertEquals("Eiffel Tower", label)
    }

    @Test
    fun hasCoordinatesFlag() {
        val coords = DeepLinkParser.parseUri("geo:48.8566,2.3522")
        assertTrue(coords!!.hasCoordinates)
        val query = DeepLinkParser.parseUri("geo:0,0?q=Berlin")
        assertTrue(!query!!.hasCoordinates)
    }
}
