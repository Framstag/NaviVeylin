package com.naviveylin.share

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedLocationParserTest {

    private fun parser(resolver: (String) -> String? = { null }) = SharedLocationParser(resolver)

    @Test
    fun geoCoordinate_parses() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        val req = parser().parse(intent)
        assertEquals(48.8566, req!!.lat!!, 1e-6)
        assertEquals(2.3522, req.lon!!, 1e-6)
        assertEquals("48.85660, 2.35220", req.label)
    }

    @Test
    fun subjectUsedAsLabel() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "Eiffel Tower")
        val req = parser().parse(intent)
        assertEquals("Eiffel Tower", req!!.label)
    }

    @Test
    fun addressText_becomesQuery() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Brandenburger Tor, Berlin")
        }
        val req = parser().parse(intent)
        assertEquals("Brandenburger Tor, Berlin", req!!.query)
        assertEquals(false, req.hasCoordinates)
    }

    @Test
    fun shortLink_resolved() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.app.goo.gl/abc123"))
        val resolver: (String) -> String? = { "https://maps.google.com/?q=48.8566,2.3522" }
        val req = parser(resolver).parse(intent)
        assertEquals(48.8566, req!!.lat!!, 1e-6)
        assertEquals(2.3522, req.lon!!, 1e-6)
    }

    @Test
    fun shortLink_unresolvable_becomesQuery() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.app.goo.gl/abc123"))
        val req = parser().parse(intent)
        assertEquals("https://maps.app.goo.gl/abc123", req!!.query)
        assertEquals(false, req.hasCoordinates)
    }

    @Test
    fun nullIntent_returnsNull() = runTest {
        assertNull(parser().parse(null))
    }

    @Test
    fun emptyIntent_returnsNull() = runTest {
        assertNull(parser().parse(Intent()))
    }
}
