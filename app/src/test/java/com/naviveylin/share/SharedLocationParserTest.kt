package com.naviveylin.share

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedLocationParserTest {

    private fun parser(resolver: (String) -> String? = { null }) = SharedLocationParser(resolver)

    private fun request(outcome: SharedLocationOutcome?) = (outcome as? SharedLocationOutcome.Request)?.request

    @Test
    fun geoCoordinate_parsesWithoutASynthesizedLabel() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        val req = request(parser().parse(intent))
        assertEquals(48.8566, req!!.lat!!, 1e-6)
        assertEquals(2.3522, req.lon!!, 1e-6)
        assertNull(
            "no synthesized coordinate-pair label: it would be a position in every log line " +
                "the request reaches (spec: auto-diagnostics — Diagnostics carry no coordinates)",
            req.label
        )
    }

    @Test
    fun subjectUsedAsLabel() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:48.8566,2.3522"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "Eiffel Tower")
        val req = request(parser().parse(intent))
        assertEquals("Eiffel Tower", req!!.label)
    }

    /**
     * A Google Maps place link with a coordinate and a subject: the subject is the label
     * (spec: share/location-receiving — Place name from the share subject).
     */
    @Test
    fun subjectWinsAsLabel() = runTest {
        val intent = shareIntent(
            text = "https://www.google.com/maps/place/Eiffel+Tower/@48.8583701,2.2944813,17z/data=!3m1!4b1!8m2!3d48.8583701!4d2.2944813",
            subject = "Eiffel Tower"
        )

        val req = request(parser().parse(intent))

        assertEquals(48.8583701, req!!.lat!!, 1e-6)
        assertEquals("Eiffel Tower", req.label)
    }

    /**
     * The same link without a subject: the place name the link itself carries is the label, and
     * the coordinate stays the destination (spec: share/location-receiving — Google Maps place
     * link with data coordinates).
     */
    @Test
    fun linkNameIsTheLabelWithoutSubject() = runTest {
        val intent = shareIntent(
            text = "https://www.google.com/maps/place/Eiffel+Tower/@48.8583701,2.2944813,17z/data=!3m1!4b1!8m2!3d48.8583701!4d2.2944813"
        )

        val req = request(parser().parse(intent))

        assertEquals(48.8583701, req!!.lat!!, 1e-6)
        assertEquals("Eiffel Tower", req.label)
    }

    /**
     * A place link without a coordinate yields the place name as the query, so the phone
     * searches for the name and never for the link (spec: share/location-receiving — Place name
     * from the link path).
     */
    @Test
    fun placeLinkWithoutCoordinate_becomesQuery() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/place/Eiffel+Tower"))

        val req = request(parser().parse(intent))

        assertNull(req!!.lat)
        assertEquals("Eiffel Tower", req.query)
    }

    @Test
    fun addressText_becomesQuery() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Brandenburger Tor, Berlin")
        }
        val req = request(parser().parse(intent))
        assertEquals("Brandenburger Tor, Berlin", req!!.query)
        assertEquals(false, req.hasCoordinates)
    }

    @Test
    fun shortLink_resolved() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.app.goo.gl/abc123"))
        val resolver: (String) -> String? = { "https://maps.google.com/?q=48.8566,2.3522" }
        val req = request(parser(resolver).parse(intent))
        assertEquals(48.8566, req!!.lat!!, 1e-6)
        assertEquals(2.3522, req.lon!!, 1e-6)
    }

    /**
     * An unresolvable short link is reported as unusable, never handed on as a query (spec:
     * share/location-receiving — An unreadable shared map link is reported, never searched).
     *
     * The production default of `shortLinkResolver` is the shared `:core` transport
     * (`ShortLinkResolver::resolveBlocking`, one redirect walk for both surfaces); its behaviour is
     * exercised in `:core`'s `ShortLinkResolverTest`, and the cases here inject a resolver so no
     * test waits on the network.
     */
    @Test
    fun unusableLinkIsNotAQuery() = runTest {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.app.goo.gl/abc123"))

        val outcome = parser().parse(intent)

        assertEquals(SharedLocationOutcome.UnusableLink, outcome)
        assertNull("an unreadable link must not become a query", request(outcome))
    }

    /** A non-map URL shared as text is a link we cannot read — reported, not searched. */
    @Test
    fun urlTextIsReportedAsUnreadableLink() = runTest {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/foo")
        }

        assertEquals(SharedLocationOutcome.UnusableLink, parser().parse(intent))
    }

    @Test
    fun nullIntent_returnsNull() = runTest {
        assertNull(parser().parse(null))
    }

    @Test
    fun emptyIntent_returnsNull() = runTest {
        assertNull(parser().parse(Intent()))
    }

    private fun shareIntent(text: String, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
}
