package com.naviveylin.core

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

    /**
     * The destination payload of a parse, or null for [DeepLinkResult.UnusableLink] and
     * [DeepLinkResult.None] — the per-format assertions read the payload this way. The
     * outcome itself is asserted where it matters (unreadable link) below.
     */
    private fun parseUri(uri: String?, resolver: (String) -> String? = { null }): DeepLinkDestination? =
        DeepLinkParser.parseUri(uri, resolver).destination

    private fun parse(intent: Intent?): DeepLinkDestination? = DeepLinkParser.parse(intent).destination

    // --- geo URIs ---

    @Test
    fun geo_plainCoordinates() {
        val dest = parseUri("geo:48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertNull(dest.query)
    }

    @Test
    fun geo_coordinatesWithLabel() {
        val dest = parseUri("geo:48.8566,2.3522?q=Eiffel%20Tower")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun geo_zeroZeroWithCoordinatesInQuery() {
        val dest = parseUri("geo:0,0?q=48.8566,2.3522(Eiffel%20Tower)")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun geo_zeroZeroWithAddressQuery() {
        val dest = parseUri("geo:0,0?q=Berlin%20Hauptbahnhof")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertNull(dest.lon)
        assertEquals("Berlin Hauptbahnhof", dest.query)
    }

    @Test
    fun geo_queryWithUnencodedCoordinates() {
        val dest = parseUri("geo:0,0?q=48.8566, 2.3522 Paris")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    // --- Google Maps URLs ---

    @Test
    fun mapsUrl_qCoordinates() {
        val dest = parseUri("https://maps.google.com/?q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_qAddress() {
        val dest = parseUri("https://maps.google.com/?q=Eiffel+Tower")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun mapsUrl_daddrQueryParam() {
        val dest = parseUri("https://maps.google.com/?daddr=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_destinationQueryParam() {
        val dest = parseUri("https://maps.google.com/?destination=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_shortLink() {
        val dest = parseUri("https://maps.app.goo.gl/abc123?q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun mapsUrl_wrongHostRejected() {
        assertNull(parseUri("https://example.com/?q=48.8566,2.3522"))
    }

    // --- modern Google Maps share links (scenario: Google Maps place link with data coordinates) ---

    @Test
    fun mapsPlaceLinkWithDataCoordinates() {
        val link = "https://www.google.com/maps/place/Eiffel+Tower/" +
            "@48.8584,2.2945,17z/data=!3m1!4b1!4m6!3m5!1s0x47e66e2964e34e2d:0x8ddca9ee380ef7e0" +
            "!8m2!3d48.8583701!4d2.2944813!16zL20vMDJqODE"

        val dest = parseUri(link)

        assertNotNull(dest)
        assertEquals(48.8583701, dest!!.lat!!, 1e-6)
        assertEquals(2.2944813, dest.lon!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun mapsViewportLink() {
        val dest = parseUri("https://www.google.com/maps/@48.8584,2.2945,17z")

        assertNotNull(dest)
        assertEquals(48.8584, dest!!.lat!!, 1e-6)
        assertEquals(2.2945, dest.lon!!, 1e-6)
        assertNull(dest.query)
    }

    @Test
    fun mapsDirLinkViewportCoordinates() {
        val dest = parseUri("https://www.google.com/maps/dir//48.8566,2.3522/@48.8566,2.3522,15z")

        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun coordinatePriorityFavorsPlacePin() {
        // Viewport centre differs from the place pin: the pin is the destination.
        val link = "https://www.google.com/maps/place/Eiffel+Tower/@48.9000,2.3000,17z" +
            "/data=!3m1!4b1!8m2!3d48.8583701!4d2.2944813"

        val dest = parseUri(link)

        assertNotNull(dest)
        assertEquals(48.8583701, dest!!.lat!!, 1e-6)
        assertEquals(2.2944813, dest.lon!!, 1e-6)
    }

    @Test
    fun queryParameterLinkVariants() {
        val search = parseUri("https://www.google.com/maps/search/?api=1&query=48.8566,2.3522")
        assertEquals(48.8566, search!!.lat!!, 1e-6)
        assertEquals(2.3522, search.lon!!, 1e-6)

        val named = parseUri("https://www.google.com/maps/search/?api=1&query=Eiffel+Tower")
        assertNull(named!!.lat)
        assertEquals("Eiffel Tower", named.query)

        val dir = parseUri("https://www.google.com/maps/dir/?api=1&destination=48.8566,2.3522")
        assertEquals(48.8566, dir!!.lat!!, 1e-6)
    }

    @Test
    fun countryHostAndLegacyShortPath() {
        val german = parseUri("https://www.google.de/maps/place/Eiffel+Tower/@48.8584,2.2945,17z")
        assertEquals(48.8584, german!!.lat!!, 1e-6)
        assertEquals("Eiffel Tower", german.query)

        val legacy = parseUri("https://goo.gl/maps/abc123?q=48.8566,2.3522")
        assertEquals(48.8566, legacy!!.lat!!, 1e-6)
        assertEquals(2.3522, legacy.lon!!, 1e-6)
    }

    @Test
    fun trackingParametersDoNotDisturbParsing() {
        val plain = parseUri("https://www.google.com/maps/place/Eiffel+Tower/@48.8583701,2.2944813,17z")
        val tracked = parseUri(
            "https://www.google.com/maps/place/Eiffel+Tower/@48.8583701,2.2944813,17z" +
                "?g_st=ic&entry=ttu&g_ep=EgoyMDI0MDkwNC4wIKXMDSoASAFQAw%3D%3D"
        )

        assertEquals(plain!!.lat, tracked!!.lat)
        assertEquals(plain.lon, tracked.lon)
        assertEquals(plain.query, tracked.query)
    }

    @Test
    fun placeNameFromLinkPathIsTheQueryLabel() {
        val dest = parseUri("https://www.google.com/maps/place/Eiffel+Tower")

        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun placeCoordinatesInPath() {
        val dest = parseUri("https://www.google.com/maps/place/48.8566,2.3522")

        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
        assertNull(dest.query)
    }

    @Test
    fun isShortLinkOnlyForAShortLink() {
        val realLink = "https://maps.app.goo.gl/WLcJAn72nT6AYxE16"
        assertTrue(DeepLinkParser.isShortLink(realLink))
        assertTrue(DeepLinkParser.isShortLink("https://maps.app.goo.gl/abc123?g_st=ic"))
        assertTrue(DeepLinkParser.isShortLink("https://goo.gl/maps/abc123"))

        assertTrue(!DeepLinkParser.isShortLink("geo:48.8566,2.3522"))
        assertTrue(!DeepLinkParser.isShortLink("https://www.google.com/maps/place/Eiffel+Tower"))
        assertTrue(!DeepLinkParser.isShortLink("https://maps.google.com/?q=48.8566,2.3522"))
        assertTrue(!DeepLinkParser.isShortLink("48.8566, 2.3522"))
        assertTrue(!DeepLinkParser.isShortLink(null))
    }

    // --- unreadable links are reported, never searched ---

    @Test
    fun unresolvableShortLinkIsUnusable() {
        val resolver: (String) -> String? = { null }

        assertEquals(DeepLinkResult.UnusableLink, DeepLinkParser.parseUri("https://maps.app.goo.gl/abc123", resolver))
    }

    @Test
    fun shortLinkNoResolverIsUnusable() {
        assertEquals(DeepLinkResult.UnusableLink, DeepLinkParser.parseUri("https://maps.app.goo.gl/abc123"))
    }

    @Test
    fun shortLinkResolvingToANonMapTargetIsUnusable() {
        val resolver: (String) -> String? = { "https://example.com/not-a-map" }

        assertEquals(DeepLinkResult.UnusableLink, DeepLinkParser.parseUri("https://maps.app.goo.gl/abc123", resolver))
    }

    @Test
    fun opaquePlaceIdLinkIsUnusable() {
        assertEquals(DeepLinkResult.UnusableLink, DeepLinkParser.parseUri("https://maps.google.com/?cid=1234567890"))
        assertEquals(
            DeepLinkResult.UnusableLink,
            DeepLinkParser.parseUri(
                "https://www.google.com/maps/place//data=!4m2!3m1!1s0x47e66e2964e34e2d:0x8ddca9ee380ef7e0"
            )
        )
    }

    @Test
    fun urlTextIsNeverAQuery() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/foo")
        }

        assertEquals(DeepLinkResult.UnusableLink, DeepLinkParser.parse(intent))
    }

    @Test
    fun linkEmbeddedInShareText() {
        val resolver: (String) -> String? = { "https://www.google.com/maps/place/Eiffel+Tower/@48.8584,2.2945,17z" }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Eiffel Tower\nhttps://maps.app.goo.gl/abc123")
        }

        val dest = DeepLinkParser.parse(intent, resolver).destination

        assertEquals(48.8584, dest!!.lat!!, 1e-6)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun unreadableLinkInShareTextFallsBackToTheSurroundingName() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Eiffel Tower\nhttps://example.com/foo")
        }

        val dest = DeepLinkParser.parse(intent).destination

        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    @Test
    fun geoUriInShareText() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "geo:0,0?q=Berlin")
        }

        val dest = DeepLinkParser.parse(intent).destination

        assertNull(dest!!.lat)
        assertEquals("Berlin", dest.query)
    }

    // --- google.navigation scheme ---

    @Test
    fun googleNavigation_qCoordinates() {
        val dest = parseUri("google.navigation:q=48.8566,2.3522")
        assertNotNull(dest)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }

    @Test
    fun googleNavigation_qAddress() {
        val dest = parseUri("google.navigation:q=Eiffel+Tower")
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Eiffel Tower", dest.query)
    }

    // --- Intent-level parsing ---

    @Test
    fun intent_geoData() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        val dest = parse(intent)
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
        val dest = parse(intent)
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
        val dest = parse(intent)
        assertNotNull(dest)
        assertNull(dest!!.lat)
        assertEquals("Brandenburg Gate Berlin", dest.query)
    }

    @Test
    fun intent_extraQuery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            putExtra("android.intent.extra.QUERY", "Kölner Dom")
        }
        val dest = parse(intent)
        assertNotNull(dest)
        assertEquals("Kölner Dom", dest!!.query)
    }

    @Test
    fun intent_extraTextWinsOverQuery() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "48.8566, 2.3522")
            putExtra("android.intent.extra.QUERY", "ignore me")
        }
        val dest = parse(intent)
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
    }

    // --- malformed input ---

    @Test
    fun nullIntent() {
        assertEquals(DeepLinkResult.None, DeepLinkParser.parse(null))
    }

    @Test
    fun nullUri() {
        assertNull(parseUri(null))
        assertNull(parseUri(""))
        assertNull(parseUri("   "))
    }

    @Test
    fun unsupportedScheme() {
        assertNull(parseUri("ftp://host/file"))
    }

    @Test
    fun geoWithoutCoordinates() {
        assertNull(parseUri("geo:"))
    }

    @Test
    fun invalidCoordinates() {
        assertNull(parseUri("geo:abc,def"))
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
        assertNull(parse(Intent()))
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
        val coords = parseUri("geo:48.8566,2.3522")
        assertTrue(coords!!.hasCoordinates)
        val query = parseUri("geo:0,0?q=Berlin")
        assertTrue(!query!!.hasCoordinates)
    }

    // --- OSM URLs ---

    @Test
    fun osmUrl_mlatMlon() {
        val dest = parseUri("https://www.openstreetmap.org/?mlat=48.8566&mlon=2.3522")
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest!!.lon!!, 1e-6)
        assertTrue(dest.hasCoordinates)
    }

    @Test
    fun osmUrl_mapHash() {
        val dest = parseUri("https://www.openstreetmap.org/#map=16/48.8566/2.3522")
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest!!.lon!!, 1e-6)
    }

    @Test
    fun osmUrl_shortHost() {
        val dest = parseUri("https://osm.org/#map=16/48.8566/2.3522")
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest!!.lon!!, 1e-6)
    }

    @Test
    fun osmUrl_invalidCoords() {
        assertNull(parseUri("https://www.openstreetmap.org/?mlat=abc&mlon=2.3522"))
        assertNull(parseUri("https://www.openstreetmap.org/#map=16/91/2.3522"))
    }

    // --- Apple / Waze URLs ---

    @Test
    fun appleMapsUrl_ll() {
        val dest = parseUri("https://maps.apple.com/?ll=48.8566,2.3522&q=Eiffel%20Tower")
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest!!.lon!!, 1e-6)
    }

    @Test
    fun wazeUrl_ll() {
        val dest = parseUri("https://waze.com/ul?ll=48.8566,2.3522&navigate=yes")
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest!!.lon!!, 1e-6)
    }

    // --- DMS / hemisphere text ---

    @Test
    fun dmsText() {
        val dest = parseUri("geo:0,0?q=48%C2%B051'23.8%22N%202%C2%B021'8.0%22E")
        // 48°51'23.8"N 2°21'8.0"E
        assertEquals(48.8566, dest!!.lat!!, 1e-3)
        assertEquals(2.3522, dest!!.lon!!, 1e-3)
    }

    @Test
    fun dmsText_plain() {
        val (lat, lon, _) = DeepLinkParser.extractCoordinatesFromQuery("48°51'23.8\"N 2°21'8.0\"E")
        assertEquals(48.8566, lat!!, 1e-3)
        assertEquals(2.3522, lon!!, 1e-3)
    }

    @Test
    fun dmsText_southernWestern() {
        val (lat, lon, _) = DeepLinkParser.extractCoordinatesFromQuery("33°51'41.0\"S 151°12'40.9\"E")
        assertEquals(-33.8614, lat!!, 1e-3)
        assertEquals(151.2114, lon!!, 1e-3)
    }

    @Test
    fun hemisphereText() {
        val (lat, lon, _) = DeepLinkParser.extractCoordinatesFromQuery("48.8566N 2.3522E")
        assertEquals(48.8566, lat!!, 1e-6)
        assertEquals(2.3522, lon!!, 1e-6)
    }

    @Test
    fun hemisphereText_southernWestern() {
        val (lat, lon, _) = DeepLinkParser.extractCoordinatesFromQuery("33.8614S 151.2114W")
        assertEquals(-33.8614, lat!!, 1e-6)
        assertEquals(-151.2114, lon!!, 1e-6)
    }

    // --- short-link resolution ---

    @Test
    fun shortLink_resolved() {
        val resolver: (String) -> String? = { "https://maps.google.com/?q=48.8566,2.3522" }
        val dest = DeepLinkParser.parseUri("https://maps.app.goo.gl/abc123", resolver).destination
        assertEquals(48.8566, dest!!.lat!!, 1e-6)
        assertEquals(2.3522, dest.lon!!, 1e-6)
    }
}
